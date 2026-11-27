package com.shopflash.modules.storage.service;

import com.shopflash.modules.storage.model.CatalogProduct;
import com.shopflash.modules.storage.repository.ProductProjectionRepository;
import com.shopflash.modules.storage.repository.StorageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Đồng bộ product projection Redis cho các Product bị một transaction Oracle thay đổi. Module khác (Checkout,
 * Flash Sale) gọi {@link #afterCommit(Collection)} bên trong transaction của mình; Redis chỉ được ghi khi
 * transaction đó COMMIT, rollback thì không ghi gì.
 *
 * <p>Dữ liệu ghi luôn đọc lại từ Oracle sau commit. Hai transaction cùng sửa một Product có thể đọc và ghi Redis
 * xen kẽ, khiến bản đọc trước ghi sau cùng. Vì vậy sau khi ghi, service đọc lại Oracle; Product nào đã đổi so với
 * bản vừa ghi thì ghi lại. Người ghi sau cùng luôn kiểm tra lại sau lần ghi của mình, nên Redis hội tụ về trạng
 * thái Oracle mới nhất.
 *
 * <p>Lỗi Redis hoặc Oracle ở bước này chỉ ghi log: dữ liệu gốc đã commit, projection tạm cũ và sửa được bằng
 * rebuild. Không ném lỗi về caller để khách không hiểu nhầm là đơn chưa được tạo.
 */
@Service
public class ProductProjectionSync {

    private static final Logger log = LoggerFactory.getLogger(ProductProjectionSync.class);
    static final int MAX_ROUNDS = 3;

    private final StorageRepository repository;
    private final ProductProjectionRepository projection;
    private final TransactionTemplate outsideTransaction;

    public ProductProjectionSync(StorageRepository repository, ProductProjectionRepository projection,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.projection = projection;
        // Tạm tách khỏi transaction vừa kết thúc: mỗi câu SELECT chạy riêng nên thấy dữ liệu commit mới nhất.
        this.outsideTransaction = new TransactionTemplate(transactions);
        this.outsideTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /** Hẹn đồng bộ các Product sau khi transaction hiện tại commit; ngoài transaction thì đồng bộ ngay. */
    public void afterCommit(Collection<Long> productIds) {
        TreeSet<Long> ids = new TreeSet<>(productIds);
        if (ids.isEmpty()) return;
        schedule(() -> ids);
    }

    /** Như {@link #afterCommit(Collection)}, nhưng danh sách Product được lấy sau commit (ví dụ theo Category). */
    void afterCommitLookup(Supplier<Collection<Long>> productIds) {
        schedule(productIds);
    }

    private void schedule(Supplier<Collection<Long>> productIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncQuietly(productIds);
            return;
        }
        // afterCompletion chạy sau mọi afterCommit và sau hook đăng ký trước (ví dụ confirm reservation Flash Sale),
        // nên Redis chậm không làm trễ bước chốt nghiệp vụ.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) syncQuietly(productIds);
            }
        });
    }

    private void syncQuietly(Supplier<Collection<Long>> productIds) {
        try {
            outsideTransaction.executeWithoutResult(status -> sync(productIds.get()));
        } catch (RuntimeException e) {
            log.warn("Oracle đã commit nhưng chưa đồng bộ được product projection Redis; dùng rebuild để sửa", e);
        }
    }

    /** Ghi rồi đọc lại Oracle; Product nào đổi trong lúc ghi thì ghi lại, tối đa {@link #MAX_ROUNDS} vòng. */
    void sync(Collection<Long> productIds) {
        List<CatalogProduct> pending = repository.findProjectionByIds(new TreeSet<>(productIds));
        for (int round = 1; !pending.isEmpty(); round++) {
            projection.upsertAll(pending);
            Map<Long, Map<String, Object>> written = new HashMap<>();
            pending.forEach(p -> written.put(p.product().productId(), ProductProjectionRepository.fields(p)));
            pending = repository.findProjectionByIds(written.keySet()).stream()
                    .filter(p -> !ProductProjectionRepository.fields(p).equals(written.get(p.product().productId())))
                    .toList();
            if (!pending.isEmpty() && round == MAX_ROUNDS) {
                log.warn("Product projection {} vẫn đổi sau {} vòng đồng bộ; lần commit kế tiếp hoặc rebuild sẽ sửa",
                        pending.stream().map(p -> p.product().productId()).toList(), MAX_ROUNDS);
                return;
            }
        }
    }
}
