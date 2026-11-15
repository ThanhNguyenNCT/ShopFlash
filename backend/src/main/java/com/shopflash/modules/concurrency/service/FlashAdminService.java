package com.shopflash.modules.concurrency.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.dto.request.FlashItemsRequest;
import com.shopflash.modules.concurrency.dto.request.FlashSaleSaveRequest;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Config;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Detail;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.EligibleProduct;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Item;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Page;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Summary;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository.NewItem;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository.ProductStock;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository.SaleRow;

/**
 * Admin tạo, sửa, xóa chương trình và danh sách sản phẩm (AGENTS.md, "Vòng đời chương trình"). Sửa dùng version
 * (optimistic lock): form của admin mang version lúc mở; version đã đổi thì 409 FLASH_SALE_CHANGED.
 */
@Service
public class FlashAdminService {

    static final Set<String> STATUSES = Set.of("DRAFT", "SCHEDULED", "ACTIVE", "ENDED", "CANCELLED");
    static final int DEFAULT_MAX_PER_USER = 1;
    static final int DEFAULT_TTL_SECONDS = 60;

    private final FlashAdminRepository repo;
    private final TransactionTemplate transaction;

    public FlashAdminService(FlashAdminRepository repo, PlatformTransactionManager transactions) {
        this.repo = repo;
        this.transaction = new TransactionTemplate(transactions);
    }

    public Page<Summary> page(String status, int page, int size) {
        String filter = status == null || status.isBlank() ? null : status.strip().toUpperCase(Locale.ROOT);
        if (filter != null && !STATUSES.contains(filter)) {
            throw ApiException.badRequest("status", "Trạng thái không hợp lệ");
        }
        List<Summary> rows = repo.page(filter, (page - 1) * size, size).stream()
                .map(s -> new Summary(s.saleId(), s.name(), s.status(), FlashTime.vn(s.startAt()),
                        FlashTime.vn(s.endAt()), s.itemCount(), s.allocatedTotal(), s.soldTotal()))
                .toList();
        return new Page<>(rows, page, size, repo.count(filter));
    }

    public Detail detail(long saleId) {
        SaleRow sale = repo.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
        List<Item> items = repo.items(saleId).stream()
                .map(i -> new Item(i.itemId(), i.productId(), i.sku(), i.productName(), i.productStatus(),
                        i.basePrice(), i.salePrice(), i.allocated(), i.sold(), i.status(), i.onHand(),
                        i.reservedFlash(), i.onHand() - i.reservedFlash()))
                .toList();
        return new Detail(sale.saleId(), sale.name(), sale.description(), sale.status(), FlashTime.vn(sale.startAt()),
                FlashTime.vn(sale.endAt()), sale.version(),
                new Config(sale.maxPerUser(), sale.reservationTtlSeconds(), sale.allowCancel()), items,
                FlashTime.vn(sale.createdAt()), FlashTime.vn(sale.updatedAt()));
    }

    public Detail create(FlashSaleSaveRequest request) {
        Fields f = fields(request);
        long saleId = transaction.execute(s -> {
            long id = repo.insertSale(f.name(), f.description(), request.startAt(), request.endAt());
            repo.insertConfig(id, f.maxPerUser(), f.ttlSeconds());
            return id;
        });
        return detail(saleId);
    }

    /** DRAFT: sửa hết. SCHEDULED/ACTIVE: chỉ tên, mô tả. ENDED/CANCELLED: không sửa. */
    public Detail update(long saleId, FlashSaleSaveRequest request) {
        if (request.version() == null) {
            throw ApiException.badRequest("version", "Thiếu version của chương trình");
        }
        Fields f = fields(request);
        SaleRow current = repo.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
        boolean ok;
        switch (current.status()) {
            case "DRAFT" -> ok = Boolean.TRUE.equals(transaction.execute(s -> {
                if (!repo.updateDraft(saleId, request.version(), f.name(), f.description(), request.startAt(),
                        request.endAt())) {
                    return false;
                }
                repo.updateConfig(saleId, f.maxPerUser(), f.ttlSeconds());
                return true;
            }));
            case "SCHEDULED", "ACTIVE" -> {
                boolean sameSchedule = current.startAt().isEqual(request.startAt())
                        && current.endAt().isEqual(request.endAt())
                        && current.maxPerUser() == f.maxPerUser()
                        && current.reservationTtlSeconds() == f.ttlSeconds();
                if (!sameSchedule) {
                    throw invalidTransition("Chương trình đã lên lịch chỉ sửa được tên và mô tả; muốn đổi giờ hoặc"
                            + " giới hạn thì hủy lịch về DRAFT trước");
                }
                ok = repo.updateInfo(saleId, request.version(), f.name(), f.description());
            }
            default -> throw invalidTransition("Chương trình đã kết thúc hoặc đã hủy, không sửa được");
        }
        if (!ok) {
            throw whyNot(saleId, request.version());
        }
        return detail(saleId);
    }

    /** Thay toàn bộ danh sách sản phẩm của chương trình DRAFT, trong một transaction. */
    public Detail replaceItems(long saleId, FlashItemsRequest request) {
        List<NewItem> wanted = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (FlashItemsRequest.Item item : request.items()) {
            if (!seen.add(item.productId())) {
                throw ApiException.badRequest("items", "Sản phẩm " + item.productId() + " bị trùng");
            }
            wanted.add(new NewItem(item.productId(), money(item.salePrice()), item.allocatedQuantity()));
        }
        transaction.executeWithoutResult(s -> {
            if (!repo.lockDraft(saleId, request.version())) {
                throw whyNot(saleId, request.version());
            }
            SaleRow sale = repo.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
            Map<Long, ProductStock> stocks = new HashMap<>();
            repo.productStocks(seen).forEach(p -> stocks.put(p.productId(), p));
            for (NewItem item : wanted) {
                validateItem(item, stocks.get(item.productId()));
            }
            List<Long> busy = repo.productsInOverlappingSales(saleId, sale.startAt(), sale.endAt(), seen);
            if (!busy.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE",
                        "Sản phẩm đã nằm trong chương trình khác trùng giờ", Map.of("productId", busy.getFirst()),
                        null);
            }
            repo.deleteItems(saleId);
            wanted.forEach(item -> repo.insertItem(saleId, item));
        });
        return detail(saleId);
    }

    public void delete(long saleId) {
        transaction.executeWithoutResult(s -> {
            if (!repo.lockDraftForDelete(saleId)) {
                throw whyNot(saleId, null);
            }
            repo.deleteSale(saleId);
        });
    }

    public Page<EligibleProduct> eligible(long saleId, String q, int page, int size) {
        SaleRow sale = repo.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
        String like = likePattern(q);
        List<EligibleProduct> rows = repo.eligible(saleId, sale.startAt(), sale.endAt(), like, (page - 1) * size, size)
                .stream()
                .map(p -> new EligibleProduct(p.productId(), p.sku(), p.name(), p.thumbnailUrl(), p.basePrice(),
                        p.onHand(), p.reservedFlash(), p.available()))
                .toList();
        return new Page<>(rows, page, size, repo.countEligible(saleId, sale.startAt(), sale.endAt(), like));
    }

    /** Vì sao câu compare-and-set không sửa được dòng nào. */
    ApiException whyNot(long saleId, Long expectedVersion) {
        SaleRow sale = repo.sale(saleId).orElse(null);
        if (sale == null) {
            return FlashErrors.saleNotFound();
        }
        if (expectedVersion != null && sale.version() != expectedVersion) {
            return new ApiException(HttpStatus.CONFLICT, "FLASH_SALE_CHANGED",
                    "Chương trình vừa được người khác sửa, tải lại rồi thử lại",
                    Map.of("currentVersion", sale.version()), null);
        }
        return invalidTransition("Chương trình đang ở trạng thái " + sale.status());
    }

    static ApiException invalidTransition(String message) {
        return ApiException.conflict("INVALID_STATUS_TRANSITION", message);
    }

    private record Fields(String name, String description, int maxPerUser, int ttlSeconds) {
    }

    private static Fields fields(FlashSaleSaveRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw ApiException.badRequest("endAt", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        FlashSaleSaveRequest.Config config = request.config();
        if (config != null && Boolean.TRUE.equals(config.allowCancel())) {
            throw ApiException.badRequest("config.allowCancel", "Chưa hỗ trợ cho phép hủy đơn flash sale");
        }
        int maxPerUser = config == null || config.maxQuantityPerUser() == null ? DEFAULT_MAX_PER_USER
                : config.maxQuantityPerUser();
        int ttl = config == null || config.reservationTimeoutSeconds() == null ? DEFAULT_TTL_SECONDS
                : config.reservationTimeoutSeconds();
        return new Fields(text(request.name(), "name", 200, true), text(request.description(), "description", 1000,
                false), maxPerUser, ttl);
    }

    private static void validateItem(NewItem item, ProductStock product) {
        if (product == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm",
                    Map.of("productId", item.productId()), null);
        }
        if (!"ACTIVE".equals(product.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Sản phẩm đang ngừng bán",
                    Map.of("productId", item.productId()), null);
        }
        if (item.salePrice().compareTo(product.basePrice()) >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Giá sale phải thấp hơn giá gốc",
                    Map.of("field", "salePrice", "productId", item.productId()), null);
        }
        if (item.allocated() > product.available()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Số suất vượt tồn kho còn bán được (" + product.available() + ")",
                    Map.of("field", "allocatedQuantity", "productId", item.productId()), null);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value.scale() > 2 || value.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw ApiException.badRequest("salePrice", "Giá có quá hai chữ số thập phân hoặc vượt giới hạn");
        }
        return value.setScale(2);
    }

    /** Chuẩn hóa NFC, bỏ khoảng trắng hai đầu; cột VARCHAR2 giới hạn theo byte. */
    private static String text(String raw, String field, int maxBytes, boolean required) {
        String value = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (value.isEmpty()) {
            if (required) {
                throw ApiException.badRequest(field, "Không được để trống");
            }
            return null;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw ApiException.badRequest(field, "Quá dài (tối đa " + maxBytes + " byte)");
        }
        return value;
    }

    /** Mẫu LIKE viết hoa, escape % _ \ của người dùng; null nếu không lọc. */
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.strip().toUpperCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + Objects.requireNonNull(escaped) + "%";
    }
}
