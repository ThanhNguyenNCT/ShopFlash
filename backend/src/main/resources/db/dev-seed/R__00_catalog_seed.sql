-- Danh mục và sản phẩm dùng chung cho mọi module (chỉ chạy ở profile dev).
-- Tên file bắt đầu bằng R__00_catalog nên Flyway chạy file này TRƯỚC R__00_core_seed:
-- core seed lấy sản phẩm 2101/2123 để tạo hai đơn mẫu; giỏ mới không có sản phẩm.
--
-- Phần 1: sản phẩm có sẵn, giữ nguyên ID/SKU/tên/giá, thêm ảnh (Unsplash, link trực tiếp).
--   Sản phẩm 1 (CORE-001), danh mục 1: giữ để tương thích dữ liệu đã có; tồn kho chỉ tạo khi thiếu.
--   Sản phẩm 101-106, danh mục 101-102: giữ các ví dụ mua hàng/hết hàng/ngừng bán; chỉ thêm khi
--   chưa có để khỏi ghi đè giá và tồn kho đã sửa tay; ảnh chỉ điền khi đang trống.
--   105 hết hàng, 106 ngừng bán: module transaction dùng để thử nhánh từ chối.
-- Phần 2: 22 danh mục (ID 201-222) và 184 sản phẩm (ID 2001-2194 = 2000 + id DummyJSON).
--   Nguồn: DummyJSON (https://dummyjson.com/products), bỏ nhóm vehicle và motorcycle.
--   Ảnh là link trực tiếp tới cdn.dummyjson.com, không lưu file ảnh trong repo.
--   Giá quy đổi 1 USD = 25.000 VND, làm tròn đến nghìn. Mô tả giữ tiếng Anh theo nguồn.
--   Tồn kho chỉ thêm khi chưa có, không ghi đè số đã bị đơn hàng/flash sale thay đổi.
--
-- Sau khi Flyway chạy, dựng lại projection Redis: POST /api/admin/catalog/projection/rebuild (admin).

-- ===== Phần 1: sản phẩm có sẵn =====

MERGE INTO categories target
USING (SELECT 1 category_id, 'Core Products' name, 'core-products' slug, 'Minimal foundation category' description, 'ACTIVE' status FROM dual) source
ON (target.category_id = source.category_id)
WHEN MATCHED THEN UPDATE SET
  target.name = source.name, target.slug = source.slug, target.description = source.description,
  target.status = source.status, target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, source.description, source.status, TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (SELECT 1 product_id, 1 category_id, 'CORE-001' sku, 'Core Product' name, 'core-product' slug, 'Minimal foundation product' description, 100000 base_price,
  'https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&q=80&auto=format&fit=crop' thumbnail_url, 'ACTIVE' status FROM dual) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = source.status,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, source.status, TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO product_inventory target
USING (SELECT 1 product_id, 20 on_hand_quantity FROM dual) source
ON (target.product_id=source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
VALUES (source.product_id, source.on_hand_quantity, 0, 0, SYSTIMESTAMP);

MERGE INTO categories target
USING (
  SELECT 101 category_id, 'Phụ kiện điện tử' name, 'tx-phu-kien-dien-tu' slug FROM dual
  UNION ALL SELECT 102, 'Màn hình', 'tx-man-hinh' FROM dual
) source
ON (target.category_id = source.category_id)
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, 'Dữ liệu mẫu Transaction (dev)', 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP);

MERGE INTO products target
USING (
  SELECT 101 product_id, 101 category_id, 'TX-101' sku, 'Tai nghe Bluetooth' name,
    'tx-tai-nghe-bluetooth' slug, 450000 base_price, 'ACTIVE' status,
    'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&q=80&auto=format&fit=crop' thumbnail_url FROM dual
  UNION ALL SELECT 102, 101, 'TX-102', 'Bàn phím cơ', 'tx-ban-phim-co', 890000, 'ACTIVE',
    'https://images.unsplash.com/photo-1589578228447-e1a4e481c6c8?w=600&q=80&auto=format&fit=crop' FROM dual
  UNION ALL SELECT 103, 101, 'TX-103', 'Chuột không dây', 'tx-chuot-khong-day', 220000, 'ACTIVE',
    'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&q=80&auto=format&fit=crop' FROM dual
  UNION ALL SELECT 104, 102, 'TX-104', 'Màn hình 24 inch', 'tx-man-hinh-24-inch', 2790000, 'ACTIVE',
    'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=600&q=80&auto=format&fit=crop' FROM dual
  UNION ALL SELECT 105, 101, 'TX-105', 'Sạc dự phòng', 'tx-sac-du-phong', 390000, 'ACTIVE',
    'https://images.unsplash.com/photo-1566554738544-d962991c3fee?w=600&q=80&auto=format&fit=crop' FROM dual
  UNION ALL SELECT 106, 101, 'TX-106', 'Loa Bluetooth ngừng bán', 'tx-loa-bluetooth-ngung-ban', 590000, 'INACTIVE',
    'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=600&q=80&auto=format&fit=crop' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.thumbnail_url = source.thumbnail_url, target.updated_at = SYSTIMESTAMP
  WHERE target.thumbnail_url IS NULL
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug,
   'Dữ liệu mẫu Transaction (dev)', source.base_price, source.thumbnail_url, source.status, SYSTIMESTAMP, SYSTIMESTAMP);

MERGE INTO product_inventory target
USING (
  SELECT 101 product_id, 20 on_hand_quantity FROM dual
  UNION ALL SELECT 102, 12 FROM dual
  UNION ALL SELECT 103, 30 FROM dual
  UNION ALL SELECT 104, 7 FROM dual
  UNION ALL SELECT 105, 0 FROM dual
  UNION ALL SELECT 106, 5 FROM dual
) source
ON (target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
VALUES
  (source.product_id, source.on_hand_quantity, 0, 0, SYSTIMESTAMP);

-- ===== Phần 2: danh mục và sản phẩm từ DummyJSON =====

MERGE INTO categories target
USING (
  SELECT 201 category_id, 'Điện thoại' name, 'smartphones' slug, 'Điện thoại thông minh các hãng' description FROM dual
  UNION ALL SELECT 202, 'Laptop', 'laptops', 'Máy tính xách tay văn phòng và cao cấp' FROM dual
  UNION ALL SELECT 203, 'Máy tính bảng', 'tablets', 'iPad và máy tính bảng Android' FROM dual
  UNION ALL SELECT 204, 'Phụ kiện điện thoại', 'mobile-accessories', 'Tai nghe, sạc, ốp lưng, loa thông minh' FROM dual
  UNION ALL SELECT 205, 'Đồng hồ nam', 'mens-watches', 'Đồng hồ thời trang và cao cấp cho nam' FROM dual
  UNION ALL SELECT 206, 'Đồng hồ nữ', 'womens-watches', 'Đồng hồ thời trang và cao cấp cho nữ' FROM dual
  UNION ALL SELECT 207, 'Áo sơ mi nam', 'mens-shirts', 'Áo sơ mi, áo thun nam' FROM dual
  UNION ALL SELECT 208, 'Giày nam', 'mens-shoes', 'Giày thể thao và giày thời trang nam' FROM dual
  UNION ALL SELECT 209, 'Giày nữ', 'womens-shoes', 'Giày cao gót, giày bệt, dép nữ' FROM dual
  UNION ALL SELECT 210, 'Áo nữ', 'tops', 'Áo và đầm dạo phố cho nữ' FROM dual
  UNION ALL SELECT 211, 'Váy đầm', 'womens-dresses', 'Váy dạ hội, đầm thời trang' FROM dual
  UNION ALL SELECT 212, 'Túi xách nữ', 'womens-bags', 'Túi xách, balo thời trang nữ' FROM dual
  UNION ALL SELECT 213, 'Trang sức', 'womens-jewellery', 'Bông tai và trang sức thời trang' FROM dual
  UNION ALL SELECT 214, 'Kính mát', 'sunglasses', 'Kính râm chống tia UV' FROM dual
  UNION ALL SELECT 215, 'Làm đẹp', 'beauty', 'Mỹ phẩm trang điểm' FROM dual
  UNION ALL SELECT 216, 'Nước hoa', 'fragrances', 'Nước hoa nam nữ chính hãng' FROM dual
  UNION ALL SELECT 217, 'Chăm sóc cơ thể', 'skin-care', 'Sữa tắm, xà phòng, dưỡng thể' FROM dual
  UNION ALL SELECT 218, 'Nội thất', 'furniture', 'Giường, sofa, bàn ghế' FROM dual
  UNION ALL SELECT 219, 'Trang trí nhà cửa', 'home-decoration', 'Đèn, khung ảnh, cây cảnh trang trí' FROM dual
  UNION ALL SELECT 220, 'Đồ dùng nhà bếp', 'kitchen-accessories', 'Dụng cụ nấu nướng và đồ gia dụng nhà bếp' FROM dual
  UNION ALL SELECT 221, 'Thực phẩm', 'groceries', 'Rau củ, thịt cá, đồ uống, thực phẩm khô' FROM dual
  UNION ALL SELECT 222, 'Thể thao', 'sports-accessories', 'Bóng, vợt và dụng cụ thể thao' FROM dual
) source
ON (target.category_id = source.category_id)
WHEN MATCHED THEN UPDATE SET
  target.name = source.name, target.slug = source.slug, target.description = source.description,
  target.status = 'ACTIVE', target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, source.description, 'ACTIVE', TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (
  SELECT 2001 product_id, 215 category_id, 'SF-2001' sku, 'Essence Mascara Lash Princess' name, 'essence-mascara-lash-princess' slug, 250000 base_price,
    'https://cdn.dummyjson.com/product-images/beauty/essence-mascara-lash-princess/thumbnail.webp' thumbnail_url,
    'The Essence Mascara Lash Princess is a popular mascara known for its volumizing and lengthening effects. Achieve dramatic lashes with this long-lasting and cruelty-free formula.' description FROM dual
  UNION ALL SELECT 2002, 215, 'SF-2002', 'Eyeshadow Palette with Mirror', 'eyeshadow-palette-with-mirror', 500000,
    'https://cdn.dummyjson.com/product-images/beauty/eyeshadow-palette-with-mirror/thumbnail.webp',
    'The Eyeshadow Palette with Mirror offers a versatile range of eyeshadow shades for creating stunning eye looks. With a built-in mirror, it''s convenient for on-the-go makeup application.' FROM dual
  UNION ALL SELECT 2003, 215, 'SF-2003', 'Powder Canister', 'powder-canister', 375000,
    'https://cdn.dummyjson.com/product-images/beauty/powder-canister/thumbnail.webp',
    'The Powder Canister is a finely milled setting powder designed to set makeup and control shine. With a lightweight and translucent formula, it provides a smooth and matte finish.' FROM dual
  UNION ALL SELECT 2004, 215, 'SF-2004', 'Red Lipstick', 'red-lipstick', 325000,
    'https://cdn.dummyjson.com/product-images/beauty/red-lipstick/thumbnail.webp',
    'The Red Lipstick is a classic and bold choice for adding a pop of color to your lips. With a creamy and pigmented formula, it provides a vibrant and long-lasting finish.' FROM dual
  UNION ALL SELECT 2005, 215, 'SF-2005', 'Red Nail Polish', 'red-nail-polish', 225000,
    'https://cdn.dummyjson.com/product-images/beauty/red-nail-polish/thumbnail.webp',
    'The Red Nail Polish offers a rich and glossy red hue for vibrant and polished nails. With a quick-drying formula, it provides a salon-quality finish at home.' FROM dual
  UNION ALL SELECT 2006, 216, 'SF-2006', 'Calvin Klein CK One', 'calvin-klein-ck-one', 1250000,
    'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/thumbnail.webp',
    'CK One by Calvin Klein is a classic unisex fragrance, known for its fresh and clean scent. It''s a versatile fragrance suitable for everyday wear.' FROM dual
  UNION ALL SELECT 2007, 216, 'SF-2007', 'Chanel Coco Noir Eau De', 'chanel-coco-noir-eau-de', 3250000,
    'https://cdn.dummyjson.com/product-images/fragrances/chanel-coco-noir-eau-de/thumbnail.webp',
    'Coco Noir by Chanel is an elegant and mysterious fragrance, featuring notes of grapefruit, rose, and sandalwood. Perfect for evening occasions.' FROM dual
  UNION ALL SELECT 2008, 216, 'SF-2008', 'Dior J''adore', 'dior-jadore', 2250000,
    'https://cdn.dummyjson.com/product-images/fragrances/dior-j%27adore/thumbnail.webp',
    'J''adore by Dior is a luxurious and floral fragrance, known for its blend of ylang-ylang, rose, and jasmine. It embodies femininity and sophistication.' FROM dual
  UNION ALL SELECT 2009, 216, 'SF-2009', 'Dolce Shine Eau de', 'dolce-shine-eau-de', 1750000,
    'https://cdn.dummyjson.com/product-images/fragrances/dolce-shine-eau-de/thumbnail.webp',
    'Dolce Shine by Dolce & Gabbana is a vibrant and fruity fragrance, featuring notes of mango, jasmine, and blonde woods. It''s a joyful and youthful scent.' FROM dual
  UNION ALL SELECT 2010, 216, 'SF-2010', 'Gucci Bloom Eau de', 'gucci-bloom-eau-de', 2000000,
    'https://cdn.dummyjson.com/product-images/fragrances/gucci-bloom-eau-de/thumbnail.webp',
    'Gucci Bloom by Gucci is a floral and captivating fragrance, with notes of tuberose, jasmine, and Rangoon creeper. It''s a modern and romantic scent.' FROM dual
  UNION ALL SELECT 2011, 218, 'SF-2011', 'Annibale Colombo Bed', 'annibale-colombo-bed', 47500000,
    'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-bed/thumbnail.webp',
    'The Annibale Colombo Bed is a luxurious and elegant bed frame, crafted with high-quality materials for a comfortable and stylish bedroom.' FROM dual
  UNION ALL SELECT 2012, 218, 'SF-2012', 'Annibale Colombo Sofa', 'annibale-colombo-sofa', 62500000,
    'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-sofa/thumbnail.webp',
    'The Annibale Colombo Sofa is a sophisticated and comfortable seating option, featuring exquisite design and premium upholstery for your living room.' FROM dual
  UNION ALL SELECT 2013, 218, 'SF-2013', 'Bedside Table African Cherry', 'bedside-table-african-cherry', 7500000,
    'https://cdn.dummyjson.com/product-images/furniture/bedside-table-african-cherry/thumbnail.webp',
    'The Bedside Table in African Cherry is a stylish and functional addition to your bedroom, providing convenient storage space and a touch of elegance.' FROM dual
  UNION ALL SELECT 2014, 218, 'SF-2014', 'Knoll Saarinen Executive Conference Chair', 'knoll-saarinen-executive-conference-chair', 12500000,
    'https://cdn.dummyjson.com/product-images/furniture/knoll-saarinen-executive-conference-chair/thumbnail.webp',
    'The Knoll Saarinen Executive Conference Chair is a modern and ergonomic chair, perfect for your office or conference room with its timeless design.' FROM dual
  UNION ALL SELECT 2015, 218, 'SF-2015', 'Wooden Bathroom Sink With Mirror', 'wooden-bathroom-sink-with-mirror', 20000000,
    'https://cdn.dummyjson.com/product-images/furniture/wooden-bathroom-sink-with-mirror/thumbnail.webp',
    'The Wooden Bathroom Sink with Mirror is a unique and stylish addition to your bathroom, featuring a wooden sink countertop and a matching mirror.' FROM dual
  UNION ALL SELECT 2016, 221, 'SF-2016', 'Apple', 'apple', 50000,
    'https://cdn.dummyjson.com/product-images/groceries/apple/thumbnail.webp',
    'Fresh and crisp apples, perfect for snacking or incorporating into various recipes.' FROM dual
  UNION ALL SELECT 2017, 221, 'SF-2017', 'Beef Steak', 'beef-steak', 325000,
    'https://cdn.dummyjson.com/product-images/groceries/beef-steak/thumbnail.webp',
    'High-quality beef steak, great for grilling or cooking to your preferred level of doneness.' FROM dual
  UNION ALL SELECT 2018, 221, 'SF-2018', 'Cat Food', 'cat-food', 225000,
    'https://cdn.dummyjson.com/product-images/groceries/cat-food/thumbnail.webp',
    'Nutritious cat food formulated to meet the dietary needs of your feline friend.' FROM dual
  UNION ALL SELECT 2019, 221, 'SF-2019', 'Chicken Meat', 'chicken-meat', 250000,
    'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/thumbnail.webp',
    'Fresh and tender chicken meat, suitable for various culinary preparations.' FROM dual
  UNION ALL SELECT 2020, 221, 'SF-2020', 'Cooking Oil', 'cooking-oil', 125000,
    'https://cdn.dummyjson.com/product-images/groceries/cooking-oil/thumbnail.webp',
    'Versatile cooking oil suitable for frying, sautéing, and various culinary applications.' FROM dual
  UNION ALL SELECT 2021, 221, 'SF-2021', 'Cucumber', 'cucumber', 37000,
    'https://cdn.dummyjson.com/product-images/groceries/cucumber/thumbnail.webp',
    'Crisp and hydrating cucumbers, ideal for salads, snacks, or as a refreshing side.' FROM dual
  UNION ALL SELECT 2022, 221, 'SF-2022', 'Dog Food', 'dog-food', 275000,
    'https://cdn.dummyjson.com/product-images/groceries/dog-food/thumbnail.webp',
    'Specially formulated dog food designed to provide essential nutrients for your canine companion.' FROM dual
  UNION ALL SELECT 2023, 221, 'SF-2023', 'Eggs', 'eggs', 75000,
    'https://cdn.dummyjson.com/product-images/groceries/eggs/thumbnail.webp',
    'Fresh eggs, a versatile ingredient for baking, cooking, or breakfast.' FROM dual
  UNION ALL SELECT 2024, 221, 'SF-2024', 'Fish Steak', 'fish-steak', 375000,
    'https://cdn.dummyjson.com/product-images/groceries/fish-steak/thumbnail.webp',
    'Quality fish steak, suitable for grilling, baking, or pan-searing.' FROM dual
  UNION ALL SELECT 2025, 221, 'SF-2025', 'Green Bell Pepper', 'green-bell-pepper', 32000,
    'https://cdn.dummyjson.com/product-images/groceries/green-bell-pepper/thumbnail.webp',
    'Fresh and vibrant green bell pepper, perfect for adding color and flavor to your dishes.' FROM dual
  UNION ALL SELECT 2026, 221, 'SF-2026', 'Green Chili Pepper', 'green-chili-pepper', 25000,
    'https://cdn.dummyjson.com/product-images/groceries/green-chili-pepper/thumbnail.webp',
    'Spicy green chili pepper, ideal for adding heat to your favorite recipes.' FROM dual
  UNION ALL SELECT 2027, 221, 'SF-2027', 'Honey Jar', 'honey-jar', 175000,
    'https://cdn.dummyjson.com/product-images/groceries/honey-jar/thumbnail.webp',
    'Pure and natural honey in a convenient jar, perfect for sweetening beverages or drizzling over food.' FROM dual
  UNION ALL SELECT 2028, 221, 'SF-2028', 'Ice Cream', 'ice-cream', 137000,
    'https://cdn.dummyjson.com/product-images/groceries/ice-cream/thumbnail.webp',
    'Creamy and delicious ice cream, available in various flavors for a delightful treat.' FROM dual
  UNION ALL SELECT 2029, 221, 'SF-2029', 'Juice', 'juice', 100000,
    'https://cdn.dummyjson.com/product-images/groceries/juice/thumbnail.webp',
    'Refreshing fruit juice, packed with vitamins and great for staying hydrated.' FROM dual
  UNION ALL SELECT 2030, 221, 'SF-2030', 'Kiwi', 'kiwi', 62000,
    'https://cdn.dummyjson.com/product-images/groceries/kiwi/thumbnail.webp',
    'Nutrient-rich kiwi, perfect for snacking or adding a tropical twist to your dishes.' FROM dual
  UNION ALL SELECT 2031, 221, 'SF-2031', 'Lemon', 'lemon', 20000,
    'https://cdn.dummyjson.com/product-images/groceries/lemon/thumbnail.webp',
    'Zesty and tangy lemons, versatile for cooking, baking, or making refreshing beverages.' FROM dual
  UNION ALL SELECT 2032, 221, 'SF-2032', 'Milk', 'milk', 87000,
    'https://cdn.dummyjson.com/product-images/groceries/milk/thumbnail.webp',
    'Fresh and nutritious milk, a staple for various recipes and daily consumption.' FROM dual
  UNION ALL SELECT 2033, 221, 'SF-2033', 'Mulberry', 'mulberry', 125000,
    'https://cdn.dummyjson.com/product-images/groceries/mulberry/thumbnail.webp',
    'Sweet and juicy mulberries, perfect for snacking or adding to desserts and cereals.' FROM dual
  UNION ALL SELECT 2034, 221, 'SF-2034', 'Nescafe Coffee', 'nescafe-coffee', 200000,
    'https://cdn.dummyjson.com/product-images/groceries/nescafe-coffee/thumbnail.webp',
    'Quality coffee from Nescafe, available in various blends for a rich and satisfying cup.' FROM dual
  UNION ALL SELECT 2035, 221, 'SF-2035', 'Potatoes', 'potatoes', 57000,
    'https://cdn.dummyjson.com/product-images/groceries/potatoes/thumbnail.webp',
    'Versatile and starchy potatoes, great for roasting, mashing, or as a side dish.' FROM dual
  UNION ALL SELECT 2036, 221, 'SF-2036', 'Protein Powder', 'protein-powder', 500000,
    'https://cdn.dummyjson.com/product-images/groceries/protein-powder/thumbnail.webp',
    'Nutrient-packed protein powder, ideal for supplementing your diet with essential proteins.' FROM dual
  UNION ALL SELECT 2037, 221, 'SF-2037', 'Red Onions', 'red-onions', 50000,
    'https://cdn.dummyjson.com/product-images/groceries/red-onions/thumbnail.webp',
    'Flavorful and aromatic red onions, perfect for adding depth to your savory dishes.' FROM dual
  UNION ALL SELECT 2038, 221, 'SF-2038', 'Rice', 'rice', 150000,
    'https://cdn.dummyjson.com/product-images/groceries/rice/thumbnail.webp',
    'High-quality rice, a staple for various cuisines and a versatile base for many dishes.' FROM dual
  UNION ALL SELECT 2039, 221, 'SF-2039', 'Soft Drinks', 'soft-drinks', 50000,
    'https://cdn.dummyjson.com/product-images/groceries/soft-drinks/thumbnail.webp',
    'Assorted soft drinks in various flavors, perfect for refreshing beverages.' FROM dual
  UNION ALL SELECT 2040, 221, 'SF-2040', 'Strawberry', 'strawberry', 100000,
    'https://cdn.dummyjson.com/product-images/groceries/strawberry/thumbnail.webp',
    'Sweet and succulent strawberries, great for snacking, desserts, or blending into smoothies.' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = 'ACTIVE', target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, 'ACTIVE', TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (
  SELECT 2041 product_id, 221 category_id, 'SF-2041' sku, 'Tissue Paper Box' name, 'tissue-paper-box' slug, 62000 base_price,
    'https://cdn.dummyjson.com/product-images/groceries/tissue-paper-box/thumbnail.webp' thumbnail_url,
    'Convenient tissue paper box for everyday use, providing soft and absorbent tissues.' description FROM dual
  UNION ALL SELECT 2042, 221, 'SF-2042', 'Water', 'water', 25000,
    'https://cdn.dummyjson.com/product-images/groceries/water/thumbnail.webp',
    'Pure and refreshing bottled water, essential for staying hydrated throughout the day.' FROM dual
  UNION ALL SELECT 2043, 219, 'SF-2043', 'Decoration Swing', 'decoration-swing', 1500000,
    'https://cdn.dummyjson.com/product-images/home-decoration/decoration-swing/thumbnail.webp',
    'The Decoration Swing is a charming addition to your home decor. Crafted with intricate details, it adds a touch of elegance and whimsy to any room.' FROM dual
  UNION ALL SELECT 2044, 219, 'SF-2044', 'Family Tree Photo Frame', 'family-tree-photo-frame', 750000,
    'https://cdn.dummyjson.com/product-images/home-decoration/family-tree-photo-frame/thumbnail.webp',
    'The Family Tree Photo Frame is a sentimental and stylish way to display your cherished family memories. With multiple photo slots, it tells the story of your loved ones.' FROM dual
  UNION ALL SELECT 2045, 219, 'SF-2045', 'House Showpiece Plant', 'house-showpiece-plant', 1000000,
    'https://cdn.dummyjson.com/product-images/home-decoration/house-showpiece-plant/thumbnail.webp',
    'The House Showpiece Plant is an artificial plant that brings a touch of nature to your home without the need for maintenance. It adds greenery and style to any space.' FROM dual
  UNION ALL SELECT 2046, 219, 'SF-2046', 'Plant Pot', 'plant-pot', 375000,
    'https://cdn.dummyjson.com/product-images/home-decoration/plant-pot/thumbnail.webp',
    'The Plant Pot is a stylish container for your favorite plants. With a sleek design, it complements your indoor or outdoor garden, adding a modern touch to your plant display.' FROM dual
  UNION ALL SELECT 2047, 219, 'SF-2047', 'Table Lamp', 'table-lamp', 1250000,
    'https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/thumbnail.webp',
    'The Table Lamp is a functional and decorative lighting solution for your living space. With a modern design, it provides both ambient and task lighting, enhancing the atmosphere.' FROM dual
  UNION ALL SELECT 2048, 220, 'SF-2048', 'Bamboo Spatula', 'bamboo-spatula', 200000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/bamboo-spatula/thumbnail.webp',
    'The Bamboo Spatula is a versatile kitchen tool made from eco-friendly bamboo. Ideal for flipping, stirring, and serving various dishes.' FROM dual
  UNION ALL SELECT 2049, 220, 'SF-2049', 'Black Aluminium Cup', 'black-aluminium-cup', 150000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/black-aluminium-cup/thumbnail.webp',
    'The Black Aluminium Cup is a stylish and durable cup suitable for both hot and cold beverages. Its sleek black design adds a modern touch to your drinkware collection.' FROM dual
  UNION ALL SELECT 2050, 220, 'SF-2050', 'Black Whisk', 'black-whisk', 250000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/black-whisk/thumbnail.webp',
    'The Black Whisk is a kitchen essential for whisking and beating ingredients. Its ergonomic handle and sleek design make it a practical and stylish tool.' FROM dual
  UNION ALL SELECT 2051, 220, 'SF-2051', 'Boxed Blender', 'boxed-blender', 1000000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/thumbnail.webp',
    'The Boxed Blender is a powerful and compact blender perfect for smoothies, shakes, and more. Its convenient design and multiple functions make it a versatile kitchen appliance.' FROM dual
  UNION ALL SELECT 2052, 220, 'SF-2052', 'Carbon Steel Wok', 'carbon-steel-wok', 750000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/carbon-steel-wok/thumbnail.webp',
    'The Carbon Steel Wok is a versatile cooking pan suitable for stir-frying, sautéing, and deep frying. Its sturdy construction ensures even heat distribution for delicious meals.' FROM dual
  UNION ALL SELECT 2053, 220, 'SF-2053', 'Chopping Board', 'chopping-board', 325000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/chopping-board/thumbnail.webp',
    'The Chopping Board is an essential kitchen accessory for food preparation. Made from durable material, it provides a safe and hygienic surface for cutting and chopping.' FROM dual
  UNION ALL SELECT 2054, 220, 'SF-2054', 'Citrus Squeezer Yellow', 'citrus-squeezer-yellow', 225000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/citrus-squeezer-yellow/thumbnail.webp',
    'The Citrus Squeezer in Yellow is a handy tool for extracting juice from citrus fruits. Its vibrant color adds a cheerful touch to your kitchen gadgets.' FROM dual
  UNION ALL SELECT 2055, 220, 'SF-2055', 'Egg Slicer', 'egg-slicer', 175000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/egg-slicer/thumbnail.webp',
    'The Egg Slicer is a convenient tool for slicing boiled eggs evenly. It''s perfect for salads, sandwiches, and other dishes where sliced eggs are desired.' FROM dual
  UNION ALL SELECT 2056, 220, 'SF-2056', 'Electric Stove', 'electric-stove', 1250000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/electric-stove/thumbnail.webp',
    'The Electric Stove provides a portable and efficient cooking solution. Ideal for small kitchens or as an additional cooking surface for various culinary needs.' FROM dual
  UNION ALL SELECT 2057, 220, 'SF-2057', 'Fine Mesh Strainer', 'fine-mesh-strainer', 250000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/fine-mesh-strainer/thumbnail.webp',
    'The Fine Mesh Strainer is a versatile tool for straining liquids and sifting dry ingredients. Its fine mesh ensures efficient filtering for smooth cooking and baking.' FROM dual
  UNION ALL SELECT 2058, 220, 'SF-2058', 'Fork', 'fork', 100000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/fork/thumbnail.webp',
    'The Fork is a classic utensil for various dining and serving purposes. Its durable and ergonomic design makes it a reliable choice for everyday use.' FROM dual
  UNION ALL SELECT 2059, 220, 'SF-2059', 'Glass', 'glass', 125000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/glass/thumbnail.webp',
    'The Glass is a versatile and elegant drinking vessel suitable for a variety of beverages. Its clear design allows you to enjoy the colors and textures of your drinks.' FROM dual
  UNION ALL SELECT 2060, 220, 'SF-2060', 'Grater Black', 'grater-black', 275000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/grater-black/thumbnail.webp',
    'The Grater in Black is a handy kitchen tool for grating cheese, vegetables, and more. Its sleek design and sharp blades make food preparation efficient and easy.' FROM dual
  UNION ALL SELECT 2061, 220, 'SF-2061', 'Hand Blender', 'hand-blender', 875000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/hand-blender/thumbnail.webp',
    'The Hand Blender is a versatile kitchen appliance for blending, pureeing, and mixing. Its compact design and powerful motor make it a convenient tool for various recipes.' FROM dual
  UNION ALL SELECT 2062, 220, 'SF-2062', 'Ice Cube Tray', 'ice-cube-tray', 150000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/ice-cube-tray/thumbnail.webp',
    'The Ice Cube Tray is a practical accessory for making ice cubes in various shapes. Perfect for keeping your drinks cool and adding a fun element to your beverages.' FROM dual
  UNION ALL SELECT 2063, 220, 'SF-2063', 'Kitchen Sieve', 'kitchen-sieve', 200000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/kitchen-sieve/thumbnail.webp',
    'The Kitchen Sieve is a versatile tool for sifting and straining dry and wet ingredients. Its fine mesh design ensures smooth results in your cooking and baking.' FROM dual
  UNION ALL SELECT 2064, 220, 'SF-2064', 'Knife', 'knife', 375000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/knife/thumbnail.webp',
    'The Knife is an essential kitchen tool for chopping, slicing, and dicing. Its sharp blade and ergonomic handle make it a reliable choice for food preparation.' FROM dual
  UNION ALL SELECT 2065, 220, 'SF-2065', 'Lunch Box', 'lunch-box', 325000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/lunch-box/thumbnail.webp',
    'The Lunch Box is a convenient and portable container for packing and carrying your meals. With compartments for different foods, it''s perfect for on-the-go dining.' FROM dual
  UNION ALL SELECT 2066, 220, 'SF-2066', 'Microwave Oven', 'microwave-oven', 2250000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/microwave-oven/thumbnail.webp',
    'The Microwave Oven is a versatile kitchen appliance for quick and efficient cooking, reheating, and defrosting. Its compact size makes it suitable for various kitchen setups.' FROM dual
  UNION ALL SELECT 2067, 220, 'SF-2067', 'Mug Tree Stand', 'mug-tree-stand', 400000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/mug-tree-stand/thumbnail.webp',
    'The Mug Tree Stand is a stylish and space-saving solution for organizing your mugs. Keep your favorite mugs easily accessible and neatly displayed in your kitchen.' FROM dual
  UNION ALL SELECT 2068, 220, 'SF-2068', 'Pan', 'pan', 625000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/pan/thumbnail.webp',
    'The Pan is a versatile and essential cookware item for frying, sautéing, and cooking various dishes. Its non-stick coating ensures easy food release and cleanup.' FROM dual
  UNION ALL SELECT 2069, 220, 'SF-2069', 'Plate', 'plate', 100000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/plate/thumbnail.webp',
    'The Plate is a classic and essential dishware item for serving meals. Its durable and stylish design makes it suitable for everyday use or special occasions.' FROM dual
  UNION ALL SELECT 2070, 220, 'SF-2070', 'Red Tongs', 'red-tongs', 175000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/red-tongs/thumbnail.webp',
    'The Red Tongs are versatile kitchen tongs suitable for various cooking and serving tasks. Their vibrant color adds a pop of excitement to your kitchen utensils.' FROM dual
  UNION ALL SELECT 2071, 220, 'SF-2071', 'Silver Pot With Glass Cap', 'silver-pot-with-glass-cap', 1000000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/silver-pot-with-glass-cap/thumbnail.webp',
    'The Silver Pot with Glass Cap is a stylish and functional cookware item for boiling, simmering, and preparing delicious meals. Its glass cap allows you to monitor cooking progress.' FROM dual
  UNION ALL SELECT 2072, 220, 'SF-2072', 'Slotted Turner', 'slotted-turner', 225000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/slotted-turner/thumbnail.webp',
    'The Slotted Turner is a kitchen utensil designed for flipping and turning food items. Its slotted design allows excess liquid to drain, making it ideal for frying and sautéing.' FROM dual
  UNION ALL SELECT 2073, 220, 'SF-2073', 'Spice Rack', 'spice-rack', 500000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/spice-rack/thumbnail.webp',
    'The Spice Rack is a convenient organizer for your spices and seasonings. Keep your kitchen essentials within reach and neatly arranged with this stylish spice rack.' FROM dual
  UNION ALL SELECT 2074, 220, 'SF-2074', 'Spoon', 'spoon', 125000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/spoon/thumbnail.webp',
    'The Spoon is a versatile kitchen utensil for stirring, serving, and tasting. Its ergonomic design and durable construction make it an essential tool for every kitchen.' FROM dual
  UNION ALL SELECT 2075, 220, 'SF-2075', 'Tray', 'tray', 425000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/tray/thumbnail.webp',
    'The Tray is a functional and decorative item for serving snacks, appetizers, or drinks. Its stylish design makes it a versatile accessory for entertaining guests.' FROM dual
  UNION ALL SELECT 2076, 220, 'SF-2076', 'Wooden Rolling Pin', 'wooden-rolling-pin', 300000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/wooden-rolling-pin/thumbnail.webp',
    'The Wooden Rolling Pin is a classic kitchen tool for rolling out dough for baking. Its smooth surface and sturdy handles make it easy to achieve uniform thickness.' FROM dual
  UNION ALL SELECT 2077, 220, 'SF-2077', 'Yellow Peeler', 'yellow-peeler', 150000,
    'https://cdn.dummyjson.com/product-images/kitchen-accessories/yellow-peeler/thumbnail.webp',
    'The Yellow Peeler is a handy tool for peeling fruits and vegetables with ease. Its bright yellow color adds a cheerful touch to your kitchen gadgets.' FROM dual
  UNION ALL SELECT 2078, 202, 'SF-2078', 'Apple MacBook Pro 14 Inch Space Grey', 'apple-macbook-pro-14-inch-space-grey', 50000000,
    'https://cdn.dummyjson.com/product-images/laptops/apple-macbook-pro-14-inch-space-grey/thumbnail.webp',
    'The MacBook Pro 14 Inch in Space Grey is a powerful and sleek laptop, featuring Apple''s M1 Pro chip for exceptional performance and a stunning Retina display.' FROM dual
  UNION ALL SELECT 2079, 202, 'SF-2079', 'Asus Zenbook Pro Dual Screen Laptop', 'asus-zenbook-pro-dual-screen-laptop', 45000000,
    'https://cdn.dummyjson.com/product-images/laptops/asus-zenbook-pro-dual-screen-laptop/thumbnail.webp',
    'The Asus Zenbook Pro Dual Screen Laptop is a high-performance device with dual screens, providing productivity and versatility for creative professionals.' FROM dual
  UNION ALL SELECT 2080, 202, 'SF-2080', 'Huawei Matebook X Pro', 'huawei-matebook-x-pro', 35000000,
    'https://cdn.dummyjson.com/product-images/laptops/huawei-matebook-x-pro/thumbnail.webp',
    'The Huawei Matebook X Pro is a slim and stylish laptop with a high-resolution touchscreen display, offering a premium experience for users on the go.' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = 'ACTIVE', target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, 'ACTIVE', TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (
  SELECT 2081 product_id, 202 category_id, 'SF-2081' sku, 'Lenovo Yoga 920' name, 'lenovo-yoga-920' slug, 27500000 base_price,
    'https://cdn.dummyjson.com/product-images/laptops/lenovo-yoga-920/thumbnail.webp' thumbnail_url,
    'The Lenovo Yoga 920 is a 2-in-1 convertible laptop with a flexible hinge, allowing you to use it as a laptop or tablet, offering versatility and portability.' description FROM dual
  UNION ALL SELECT 2082, 202, 'SF-2082', 'New DELL XPS 13 9300 Laptop', 'new-dell-xps-13-9300-laptop', 37500000,
    'https://cdn.dummyjson.com/product-images/laptops/new-dell-xps-13-9300-laptop/thumbnail.webp',
    'The New DELL XPS 13 9300 Laptop is a compact and powerful device, featuring a virtually borderless InfinityEdge display and high-end performance for various tasks.' FROM dual
  UNION ALL SELECT 2083, 207, 'SF-2083', 'Blue & Black Check Shirt', 'blue-and-black-check-shirt', 750000,
    'https://cdn.dummyjson.com/product-images/mens-shirts/blue-%26-black-check-shirt/thumbnail.webp',
    'The Blue & Black Check Shirt is a stylish and comfortable men''s shirt featuring a classic check pattern. Made from high-quality fabric, it''s suitable for both casual and semi-formal occasions.' FROM dual
  UNION ALL SELECT 2084, 207, 'SF-2084', 'Gigabyte Aorus Men Tshirt', 'gigabyte-aorus-men-tshirt', 625000,
    'https://cdn.dummyjson.com/product-images/mens-shirts/gigabyte-aorus-men-tshirt/thumbnail.webp',
    'The Gigabyte Aorus Men Tshirt is a cool and casual shirt for gaming enthusiasts. With the Aorus logo and sleek design, it''s perfect for expressing your gaming style.' FROM dual
  UNION ALL SELECT 2085, 207, 'SF-2085', 'Man Plaid Shirt', 'man-plaid-shirt', 875000,
    'https://cdn.dummyjson.com/product-images/mens-shirts/man-plaid-shirt/thumbnail.webp',
    'The Man Plaid Shirt is a timeless and versatile men''s shirt with a classic plaid pattern. Its comfortable fit and casual style make it a wardrobe essential for various occasions.' FROM dual
  UNION ALL SELECT 2086, 207, 'SF-2086', 'Man Short Sleeve Shirt', 'man-short-sleeve-shirt', 500000,
    'https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/thumbnail.webp',
    'The Man Short Sleeve Shirt is a breezy and stylish option for warm days. With a comfortable fit and short sleeves, it''s perfect for a laid-back yet polished look.' FROM dual
  UNION ALL SELECT 2087, 207, 'SF-2087', 'Men Check Shirt', 'men-check-shirt', 700000,
    'https://cdn.dummyjson.com/product-images/mens-shirts/men-check-shirt/thumbnail.webp',
    'The Men Check Shirt is a classic and versatile shirt featuring a stylish check pattern. Suitable for various occasions, it adds a smart and polished touch to your wardrobe.' FROM dual
  UNION ALL SELECT 2088, 208, 'SF-2088', 'Nike Air Jordan 1 Red And Black', 'nike-air-jordan-1-red-and-black', 3750000,
    'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/thumbnail.webp',
    'The Nike Air Jordan 1 in Red and Black is an iconic basketball sneaker known for its stylish design and high-performance features, making it a favorite among sneaker enthusiasts and athletes.' FROM dual
  UNION ALL SELECT 2089, 208, 'SF-2089', 'Nike Baseball Cleats', 'nike-baseball-cleats', 2000000,
    'https://cdn.dummyjson.com/product-images/mens-shoes/nike-baseball-cleats/thumbnail.webp',
    'Nike Baseball Cleats are designed for maximum traction and performance on the baseball field. They provide stability and support for players during games and practices.' FROM dual
  UNION ALL SELECT 2090, 208, 'SF-2090', 'Puma Future Rider Trainers', 'puma-future-rider-trainers', 2250000,
    'https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/thumbnail.webp',
    'The Puma Future Rider Trainers offer a blend of retro style and modern comfort. Perfect for casual wear, these trainers provide a fashionable and comfortable option for everyday use.' FROM dual
  UNION ALL SELECT 2091, 208, 'SF-2091', 'Sports Sneakers Off White & Red', 'sports-sneakers-off-white-and-red', 3000000,
    'https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-%26-red/thumbnail.webp',
    'The Sports Sneakers in Off White and Red combine style and functionality, making them a fashionable choice for sports enthusiasts. The red and off-white color combination adds a bold and energetic touch.' FROM dual
  UNION ALL SELECT 2092, 208, 'SF-2092', 'Sports Sneakers Off White Red', 'sports-sneakers-off-white-red', 2750000,
    'https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-red/thumbnail.webp',
    'Another variant of the Sports Sneakers in Off White Red, featuring a unique design. These sneakers offer style and comfort for casual occasions.' FROM dual
  UNION ALL SELECT 2093, 205, 'SF-2093', 'Brown Leather Belt Watch', 'brown-leather-belt-watch', 2250000,
    'https://cdn.dummyjson.com/product-images/mens-watches/brown-leather-belt-watch/thumbnail.webp',
    'The Brown Leather Belt Watch is a stylish timepiece with a classic design. Featuring a genuine leather strap and a sleek dial, it adds a touch of sophistication to your look.' FROM dual
  UNION ALL SELECT 2094, 205, 'SF-2094', 'Longines Master Collection', 'longines-master-collection', 37500000,
    'https://cdn.dummyjson.com/product-images/mens-watches/longines-master-collection/thumbnail.webp',
    'The Longines Master Collection is an elegant and refined watch known for its precision and craftsmanship. With a timeless design, it''s a symbol of luxury and sophistication.' FROM dual
  UNION ALL SELECT 2095, 205, 'SF-2095', 'Rolex Cellini Date Black Dial', 'rolex-cellini-date-black-dial', 225000000,
    'https://cdn.dummyjson.com/product-images/mens-watches/rolex-cellini-date-black-dial/thumbnail.webp',
    'The Rolex Cellini Date with Black Dial is a classic and prestigious watch. With a black dial and date complication, it exudes sophistication and is a symbol of Rolex''s heritage.' FROM dual
  UNION ALL SELECT 2096, 205, 'SF-2096', 'Rolex Cellini Moonphase', 'rolex-cellini-moonphase', 325000000,
    'https://cdn.dummyjson.com/product-images/mens-watches/rolex-cellini-moonphase/thumbnail.webp',
    'The Rolex Cellini Moonphase is a masterpiece of horology, featuring a moon phase complication and exquisite design. It reflects Rolex''s commitment to precision and elegance.' FROM dual
  UNION ALL SELECT 2097, 205, 'SF-2097', 'Rolex Datejust', 'rolex-datejust', 275000000,
    'https://cdn.dummyjson.com/product-images/mens-watches/rolex-datejust/thumbnail.webp',
    'The Rolex Datejust is an iconic and versatile timepiece with a date window. Known for its timeless design and reliability, it''s a symbol of Rolex''s watchmaking excellence.' FROM dual
  UNION ALL SELECT 2098, 205, 'SF-2098', 'Rolex Submariner Watch', 'rolex-submariner-watch', 350000000,
    'https://cdn.dummyjson.com/product-images/mens-watches/rolex-submariner-watch/thumbnail.webp',
    'The Rolex Submariner is a legendary dive watch with a rich history. Known for its durability and water resistance, it''s a symbol of adventure and exploration.' FROM dual
  UNION ALL SELECT 2099, 204, 'SF-2099', 'Amazon Echo Plus', 'amazon-echo-plus', 2500000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/amazon-echo-plus/thumbnail.webp',
    'The Amazon Echo Plus is a smart speaker with built-in Alexa voice control. It features premium sound quality and serves as a hub for controlling smart home devices.' FROM dual
  UNION ALL SELECT 2100, 204, 'SF-2100', 'Apple Airpods', 'apple-airpods', 3250000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods/thumbnail.webp',
    'The Apple Airpods offer a seamless wireless audio experience. With easy pairing, high-quality sound, and Siri integration, they are perfect for on-the-go listening.' FROM dual
  UNION ALL SELECT 2101, 204, 'SF-2101', 'Apple AirPods Max Silver', 'apple-airpods-max-silver', 13750000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods-max-silver/thumbnail.webp',
    'The Apple AirPods Max in Silver are premium over-ear headphones with high-fidelity audio, adaptive EQ, and active noise cancellation. Experience immersive sound in style.' FROM dual
  UNION ALL SELECT 2102, 204, 'SF-2102', 'Apple Airpower Wireless Charger', 'apple-airpower-wireless-charger', 2000000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpower-wireless-charger/thumbnail.webp',
    'The Apple AirPower Wireless Charger provides a convenient way to charge your compatible Apple devices wirelessly. Simply place your devices on the charging mat for effortless charging.' FROM dual
  UNION ALL SELECT 2103, 204, 'SF-2103', 'Apple HomePod Mini Cosmic Grey', 'apple-homepod-mini-cosmic-grey', 2500000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-homepod-mini-cosmic-grey/thumbnail.webp',
    'The Apple HomePod Mini in Cosmic Grey is a compact smart speaker that delivers impressive audio and integrates seamlessly with the Apple ecosystem for a smart home experience.' FROM dual
  UNION ALL SELECT 2104, 204, 'SF-2104', 'Apple iPhone Charger', 'apple-iphone-charger', 500000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-iphone-charger/thumbnail.webp',
    'The Apple iPhone Charger is a high-quality charger designed for fast and efficient charging of your iPhone. Ensure your device stays powered up and ready to go.' FROM dual
  UNION ALL SELECT 2105, 204, 'SF-2105', 'Apple MagSafe Battery Pack', 'apple-magsafe-battery-pack', 2500000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-magsafe-battery-pack/thumbnail.webp',
    'The Apple MagSafe Battery Pack is a portable and convenient way to add extra battery life to your MagSafe-compatible iPhone. Attach it magnetically for a secure connection.' FROM dual
  UNION ALL SELECT 2106, 204, 'SF-2106', 'Apple Watch Series 4 Gold', 'apple-watch-series-4-gold', 8750000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-watch-series-4-gold/thumbnail.webp',
    'The Apple Watch Series 4 in Gold is a stylish and advanced smartwatch with features like heart rate monitoring, fitness tracking, and a beautiful Retina display.' FROM dual
  UNION ALL SELECT 2107, 204, 'SF-2107', 'Beats Flex Wireless Earphones', 'beats-flex-wireless-earphones', 1250000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/beats-flex-wireless-earphones/thumbnail.webp',
    'The Beats Flex Wireless Earphones offer a comfortable and versatile audio experience. With magnetic earbuds and up to 12 hours of battery life, they are ideal for everyday use.' FROM dual
  UNION ALL SELECT 2108, 204, 'SF-2108', 'iPhone 12 Silicone Case with MagSafe Plum', 'iphone-12-silicone-case-with-magsafe-plum', 750000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/iphone-12-silicone-case-with-magsafe-plum/thumbnail.webp',
    'The iPhone 12 Silicone Case with MagSafe in Plum is a stylish and protective case designed for the iPhone 12. It features MagSafe technology for easy attachment of accessories.' FROM dual
  UNION ALL SELECT 2109, 204, 'SF-2109', 'Monopod', 'monopod', 500000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/monopod/thumbnail.webp',
    'The Monopod is a versatile camera accessory for stable and adjustable shooting. Perfect for capturing selfies, group photos, and videos with ease.' FROM dual
  UNION ALL SELECT 2110, 204, 'SF-2110', 'Selfie Lamp with iPhone', 'selfie-lamp-with-iphone', 375000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/selfie-lamp-with-iphone/thumbnail.webp',
    'The Selfie Lamp with iPhone is a portable and adjustable LED light designed to enhance your selfies and video calls. Attach it to your iPhone for well-lit photos.' FROM dual
  UNION ALL SELECT 2111, 204, 'SF-2111', 'Selfie Stick Monopod', 'selfie-stick-monopod', 325000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/selfie-stick-monopod/thumbnail.webp',
    'The Selfie Stick Monopod is a extendable and foldable device for capturing the perfect selfie or group photo. Compatible with smartphones and cameras.' FROM dual
  UNION ALL SELECT 2112, 204, 'SF-2112', 'TV Studio Camera Pedestal', 'tv-studio-camera-pedestal', 12500000,
    'https://cdn.dummyjson.com/product-images/mobile-accessories/tv-studio-camera-pedestal/thumbnail.webp',
    'The TV Studio Camera Pedestal is a professional-grade camera support system for smooth and precise camera movements in a studio setting. Ideal for broadcast and production.' FROM dual
  UNION ALL SELECT 2118, 217, 'SF-2118', 'Attitude Super Leaves Hand Soap', 'attitude-super-leaves-hand-soap', 225000,
    'https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/thumbnail.webp',
    'Attitude Super Leaves Hand Soap is a natural and nourishing hand soap enriched with the goodness of super leaves. It cleanses and moisturizes your hands, leaving them feeling fresh and soft.' FROM dual
  UNION ALL SELECT 2119, 217, 'SF-2119', 'Olay Ultra Moisture Shea Butter Body Wash', 'olay-ultra-moisture-shea-butter-body-wash', 325000,
    'https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/thumbnail.webp',
    'Olay Ultra Moisture Shea Butter Body Wash is a luxurious body wash that hydrates and nourishes your skin with the moisturizing power of shea butter. Enjoy a rich lather and silky-smooth skin.' FROM dual
  UNION ALL SELECT 2120, 217, 'SF-2120', 'Vaseline Men Body and Face Lotion', 'vaseline-men-body-and-face-lotion', 250000,
    'https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/thumbnail.webp',
    'Vaseline Men Body and Face Lotion is a specially formulated lotion designed to provide long-lasting moisture to men''s skin. It absorbs quickly and helps keep the skin hydrated and healthy.' FROM dual
  UNION ALL SELECT 2121, 201, 'SF-2121', 'iPhone 5s', 'iphone-5s', 5000000,
    'https://cdn.dummyjson.com/product-images/smartphones/iphone-5s/thumbnail.webp',
    'The iPhone 5s is a classic smartphone known for its compact design and advanced features during its release. While it''s an older model, it still provides a reliable user experience.' FROM dual
  UNION ALL SELECT 2122, 201, 'SF-2122', 'iPhone 6', 'iphone-6', 7500000,
    'https://cdn.dummyjson.com/product-images/smartphones/iphone-6/thumbnail.webp',
    'The iPhone 6 is a stylish and capable smartphone with a larger display and improved performance. It introduced new features and design elements, making it a popular choice in its time.' FROM dual
  UNION ALL SELECT 2123, 201, 'SF-2123', 'iPhone 13 Pro', 'iphone-13-pro', 27500000,
    'https://cdn.dummyjson.com/product-images/smartphones/iphone-13-pro/thumbnail.webp',
    'The iPhone 13 Pro is a cutting-edge smartphone with a powerful camera system, high-performance chip, and stunning display. It offers advanced features for users who demand top-notch technology.' FROM dual
  UNION ALL SELECT 2124, 201, 'SF-2124', 'iPhone X', 'iphone-x', 22500000,
    'https://cdn.dummyjson.com/product-images/smartphones/iphone-x/thumbnail.webp',
    'The iPhone X is a flagship smartphone featuring a bezel-less OLED display, facial recognition technology (Face ID), and impressive performance. It represents a milestone in iPhone design and innovation.' FROM dual
  UNION ALL SELECT 2125, 201, 'SF-2125', 'Oppo A57', 'oppo-a57', 6250000,
    'https://cdn.dummyjson.com/product-images/smartphones/oppo-a57/thumbnail.webp',
    'The Oppo A57 is a mid-range smartphone known for its sleek design and capable features. It offers a balance of performance and affordability, making it a popular choice.' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = 'ACTIVE', target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, 'ACTIVE', TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (
  SELECT 2126 product_id, 201 category_id, 'SF-2126' sku, 'Oppo F19 Pro Plus' name, 'oppo-f19-pro-plus' slug, 10000000 base_price,
    'https://cdn.dummyjson.com/product-images/smartphones/oppo-f19-pro-plus/thumbnail.webp' thumbnail_url,
    'The Oppo F19 Pro Plus is a feature-rich smartphone with a focus on camera capabilities. It boasts advanced photography features and a powerful performance for a premium user experience.' description FROM dual
  UNION ALL SELECT 2127, 201, 'SF-2127', 'Oppo K1', 'oppo-k1', 7500000,
    'https://cdn.dummyjson.com/product-images/smartphones/oppo-k1/thumbnail.webp',
    'The Oppo K1 series offers a range of smartphones with various features and specifications. Known for their stylish design and reliable performance, the Oppo K1 series caters to diverse user preferences.' FROM dual
  UNION ALL SELECT 2128, 201, 'SF-2128', 'Realme C35', 'realme-c35', 3750000,
    'https://cdn.dummyjson.com/product-images/smartphones/realme-c35/thumbnail.webp',
    'The Realme C35 is a budget-friendly smartphone with a focus on providing essential features for everyday use. It offers a reliable performance and user-friendly experience.' FROM dual
  UNION ALL SELECT 2129, 201, 'SF-2129', 'Realme X', 'realme-x', 7500000,
    'https://cdn.dummyjson.com/product-images/smartphones/realme-x/thumbnail.webp',
    'The Realme X is a mid-range smartphone known for its sleek design and impressive display. It offers a good balance of performance and camera capabilities for users seeking a quality device.' FROM dual
  UNION ALL SELECT 2130, 201, 'SF-2130', 'Realme XT', 'realme-xt', 8750000,
    'https://cdn.dummyjson.com/product-images/smartphones/realme-xt/thumbnail.webp',
    'The Realme XT is a feature-rich smartphone with a focus on camera technology. It comes equipped with advanced camera sensors, delivering high-quality photos and videos for photography enthusiasts.' FROM dual
  UNION ALL SELECT 2131, 201, 'SF-2131', 'Samsung Galaxy S7', 'samsung-galaxy-s7', 7500000,
    'https://cdn.dummyjson.com/product-images/smartphones/samsung-galaxy-s7/thumbnail.webp',
    'The Samsung Galaxy S7 is a flagship smartphone known for its sleek design and advanced features. It features a high-resolution display, powerful camera, and robust performance.' FROM dual
  UNION ALL SELECT 2132, 201, 'SF-2132', 'Samsung Galaxy S8', 'samsung-galaxy-s8', 12500000,
    'https://cdn.dummyjson.com/product-images/smartphones/samsung-galaxy-s8/thumbnail.webp',
    'The Samsung Galaxy S8 is a premium smartphone with an Infinity Display, offering a stunning visual experience. It boasts advanced camera capabilities and cutting-edge technology.' FROM dual
  UNION ALL SELECT 2133, 201, 'SF-2133', 'Samsung Galaxy S10', 'samsung-galaxy-s10', 17500000,
    'https://cdn.dummyjson.com/product-images/smartphones/samsung-galaxy-s10/thumbnail.webp',
    'The Samsung Galaxy S10 is a flagship device featuring a dynamic AMOLED display, versatile camera system, and powerful performance. It represents innovation and excellence in smartphone technology.' FROM dual
  UNION ALL SELECT 2134, 201, 'SF-2134', 'Vivo S1', 'vivo-s1', 6250000,
    'https://cdn.dummyjson.com/product-images/smartphones/vivo-s1/thumbnail.webp',
    'The Vivo S1 is a stylish and mid-range smartphone offering a blend of design and performance. It features a vibrant display, capable camera system, and reliable functionality.' FROM dual
  UNION ALL SELECT 2135, 201, 'SF-2135', 'Vivo V9', 'vivo-v9', 7500000,
    'https://cdn.dummyjson.com/product-images/smartphones/vivo-v9/thumbnail.webp',
    'The Vivo V9 is a smartphone known for its sleek design and emphasis on capturing high-quality selfies. It features a notch display, dual-camera setup, and a modern design.' FROM dual
  UNION ALL SELECT 2136, 201, 'SF-2136', 'Vivo X21', 'vivo-x21', 12500000,
    'https://cdn.dummyjson.com/product-images/smartphones/vivo-x21/thumbnail.webp',
    'The Vivo X21 is a premium smartphone with a focus on cutting-edge technology. It features an in-display fingerprint sensor, a high-resolution display, and advanced camera capabilities.' FROM dual
  UNION ALL SELECT 2137, 222, 'SF-2137', 'American Football', 'american-football', 500000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/american-football/thumbnail.webp',
    'The American Football is a classic ball used in American football games. It is designed for throwing and catching, making it an essential piece of equipment for the sport.' FROM dual
  UNION ALL SELECT 2138, 222, 'SF-2138', 'Baseball Ball', 'baseball-ball', 225000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/baseball-ball/thumbnail.webp',
    'The Baseball Ball is a standard baseball used in baseball games. It features a durable leather cover and is designed for pitching, hitting, and fielding in the game of baseball.' FROM dual
  UNION ALL SELECT 2139, 222, 'SF-2139', 'Baseball Glove', 'baseball-glove', 625000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/baseball-glove/thumbnail.webp',
    'The Baseball Glove is a protective glove worn by baseball players. It is designed to catch and field the baseball, providing players with comfort and control during the game.' FROM dual
  UNION ALL SELECT 2140, 222, 'SF-2140', 'Basketball', 'basketball', 375000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/basketball/thumbnail.webp',
    'The Basketball is a standard ball used in basketball games. It is designed for dribbling, shooting, and passing in the game of basketball, suitable for both indoor and outdoor play.' FROM dual
  UNION ALL SELECT 2141, 222, 'SF-2141', 'Basketball Rim', 'basketball-rim', 1000000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/basketball-rim/thumbnail.webp',
    'The Basketball Rim is a sturdy hoop and net assembly mounted on a basketball backboard. It provides a target for shooting and scoring in the game of basketball.' FROM dual
  UNION ALL SELECT 2142, 222, 'SF-2142', 'Cricket Ball', 'cricket-ball', 325000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/cricket-ball/thumbnail.webp',
    'The Cricket Ball is a hard leather ball used in the sport of cricket. It is bowled and batted in the game, and its hardness and seam contribute to the dynamics of cricket play.' FROM dual
  UNION ALL SELECT 2143, 222, 'SF-2143', 'Cricket Bat', 'cricket-bat', 750000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/cricket-bat/thumbnail.webp',
    'The Cricket Bat is an essential piece of cricket equipment used by batsmen to hit the cricket ball. It is made of wood and comes in various sizes and designs.' FROM dual
  UNION ALL SELECT 2144, 222, 'SF-2144', 'Cricket Helmet', 'cricket-helmet', 1125000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/cricket-helmet/thumbnail.webp',
    'The Cricket Helmet is a protective headgear worn by cricket players, especially batsmen and wicketkeepers. It provides protection against fast bowling and bouncers.' FROM dual
  UNION ALL SELECT 2145, 222, 'SF-2145', 'Cricket Wicket', 'cricket-wicket', 750000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/cricket-wicket/thumbnail.webp',
    'The Cricket Wicket is a set of three stumps and two bails, forming a wicket used in the sport of cricket. Batsmen aim to protect the wicket while scoring runs.' FROM dual
  UNION ALL SELECT 2146, 222, 'SF-2146', 'Feather Shuttlecock', 'feather-shuttlecock', 150000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/feather-shuttlecock/thumbnail.webp',
    'The Feather Shuttlecock is used in the sport of badminton. It features natural feathers and is designed for high-speed play, providing stability and accuracy during matches.' FROM dual
  UNION ALL SELECT 2147, 222, 'SF-2147', 'Football', 'football', 450000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/football/thumbnail.webp',
    'The Football, also known as a soccer ball, is the standard ball used in the sport of football (soccer). It is designed for kicking and passing in the game.' FROM dual
  UNION ALL SELECT 2148, 222, 'SF-2148', 'Golf Ball', 'golf-ball', 250000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/golf-ball/thumbnail.webp',
    'The Golf Ball is a small ball used in the sport of golf. It features dimples on its surface, providing aerodynamic lift and distance when struck by a golf club.' FROM dual
  UNION ALL SELECT 2149, 222, 'SF-2149', 'Iron Golf', 'iron-golf', 1250000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/iron-golf/thumbnail.webp',
    'The Iron Golf is a type of golf club designed for various golf shots. It features a solid metal head and is used for approach shots, chipping, and other golfing techniques.' FROM dual
  UNION ALL SELECT 2150, 222, 'SF-2150', 'Metal Baseball Bat', 'metal-baseball-bat', 750000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/metal-baseball-bat/thumbnail.webp',
    'The Metal Baseball Bat is a durable and lightweight baseball bat made from metal alloys. It is commonly used in baseball games for hitting and batting practice.' FROM dual
  UNION ALL SELECT 2151, 222, 'SF-2151', 'Tennis Ball', 'tennis-ball', 175000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/tennis-ball/thumbnail.webp',
    'The Tennis Ball is a standard ball used in the sport of tennis. It is designed for bouncing and hitting with tennis rackets during matches or practice sessions.' FROM dual
  UNION ALL SELECT 2152, 222, 'SF-2152', 'Tennis Racket', 'tennis-racket', 1250000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/tennis-racket/thumbnail.webp',
    'The Tennis Racket is an essential piece of equipment used in the sport of tennis. It features a frame with strings and a grip, allowing players to hit the tennis ball.' FROM dual
  UNION ALL SELECT 2153, 222, 'SF-2153', 'Volleyball', 'volleyball', 300000,
    'https://cdn.dummyjson.com/product-images/sports-accessories/volleyball/thumbnail.webp',
    'The Volleyball is a standard ball used in the sport of volleyball. It is designed for passing, setting, and spiking over the net during volleyball matches.' FROM dual
  UNION ALL SELECT 2154, 214, 'SF-2154', 'Black Sun Glasses', 'black-sun-glasses', 750000,
    'https://cdn.dummyjson.com/product-images/sunglasses/black-sun-glasses/thumbnail.webp',
    'The Black Sun Glasses are a classic and stylish choice, featuring a sleek black frame and tinted lenses. They provide both UV protection and a fashionable look.' FROM dual
  UNION ALL SELECT 2155, 214, 'SF-2155', 'Classic Sun Glasses', 'classic-sun-glasses', 625000,
    'https://cdn.dummyjson.com/product-images/sunglasses/classic-sun-glasses/thumbnail.webp',
    'The Classic Sun Glasses offer a timeless design with a neutral frame and UV-protected lenses. These sunglasses are versatile and suitable for various occasions.' FROM dual
  UNION ALL SELECT 2156, 214, 'SF-2156', 'Green and Black Glasses', 'green-and-black-glasses', 875000,
    'https://cdn.dummyjson.com/product-images/sunglasses/green-and-black-glasses/thumbnail.webp',
    'The Green and Black Glasses feature a bold combination of green and black colors, adding a touch of vibrancy to your eyewear collection. They are both stylish and eye-catching.' FROM dual
  UNION ALL SELECT 2157, 214, 'SF-2157', 'Party Glasses', 'party-glasses', 500000,
    'https://cdn.dummyjson.com/product-images/sunglasses/party-glasses/thumbnail.webp',
    'The Party Glasses are designed to add flair to your party outfit. With unique shapes or colorful frames, they''re perfect for adding a playful touch to your look during celebrations.' FROM dual
  UNION ALL SELECT 2158, 214, 'SF-2158', 'Sunglasses', 'sunglasses', 575000,
    'https://cdn.dummyjson.com/product-images/sunglasses/sunglasses/thumbnail.webp',
    'The Sunglasses offer a classic and simple design with a focus on functionality. These sunglasses provide essential UV protection while maintaining a timeless look.' FROM dual
  UNION ALL SELECT 2159, 203, 'SF-2159', 'iPad Mini 2021 Starlight', 'ipad-mini-2021-starlight', 12500000,
    'https://cdn.dummyjson.com/product-images/tablets/ipad-mini-2021-starlight/thumbnail.webp',
    'The iPad Mini 2021 in Starlight is a compact and powerful tablet from Apple. Featuring a stunning Retina display, powerful A-series chip, and a sleek design, it offers a premium tablet experience.' FROM dual
  UNION ALL SELECT 2160, 203, 'SF-2160', 'Samsung Galaxy Tab S8 Plus Grey', 'samsung-galaxy-tab-s8-plus-grey', 15000000,
    'https://cdn.dummyjson.com/product-images/tablets/samsung-galaxy-tab-s8-plus-grey/thumbnail.webp',
    'The Samsung Galaxy Tab S8 Plus in Grey is a high-performance Android tablet by Samsung. With a large AMOLED display, powerful processor, and S Pen support, it''s ideal for productivity and entertainment.' FROM dual
  UNION ALL SELECT 2161, 203, 'SF-2161', 'Samsung Galaxy Tab White', 'samsung-galaxy-tab-white', 8750000,
    'https://cdn.dummyjson.com/product-images/tablets/samsung-galaxy-tab-white/thumbnail.webp',
    'The Samsung Galaxy Tab in White is a sleek and versatile Android tablet. With a vibrant display, long-lasting battery, and a range of features, it offers a great user experience for various tasks.' FROM dual
  UNION ALL SELECT 2162, 210, 'SF-2162', 'Blue Frock', 'blue-frock', 750000,
    'https://cdn.dummyjson.com/product-images/tops/blue-frock/thumbnail.webp',
    'The Blue Frock is a charming and stylish dress for various occasions. With a vibrant blue color and a comfortable design, it adds a touch of elegance to your wardrobe.' FROM dual
  UNION ALL SELECT 2163, 210, 'SF-2163', 'Girl Summer Dress', 'girl-summer-dress', 500000,
    'https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/thumbnail.webp',
    'The Girl Summer Dress is a cute and breezy dress designed for warm weather. With playful patterns and lightweight fabric, it''s perfect for keeping cool and stylish during the summer.' FROM dual
  UNION ALL SELECT 2164, 210, 'SF-2164', 'Gray Dress', 'gray-dress', 875000,
    'https://cdn.dummyjson.com/product-images/tops/gray-dress/thumbnail.webp',
    'The Gray Dress is a versatile and chic option for various occasions. With a neutral gray color, it can be dressed up or down, making it a wardrobe staple for any fashion-forward individual.' FROM dual
  UNION ALL SELECT 2165, 210, 'SF-2165', 'Short Frock', 'short-frock', 625000,
    'https://cdn.dummyjson.com/product-images/tops/short-frock/thumbnail.webp',
    'The Short Frock is a playful and trendy dress with a shorter length. Ideal for casual outings or special occasions, it combines style and comfort for a fashionable look.' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = 'ACTIVE', target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, 'ACTIVE', TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (
  SELECT 2166 product_id, 210 category_id, 'SF-2166' sku, 'Tartan Dress' name, 'tartan-dress' slug, 1000000 base_price,
    'https://cdn.dummyjson.com/product-images/tops/tartan-dress/thumbnail.webp' thumbnail_url,
    'The Tartan Dress features a classic tartan pattern, bringing a timeless and sophisticated touch to your wardrobe. Perfect for fall and winter, it adds a hint of traditional charm.' description FROM dual
  UNION ALL SELECT 2172, 212, 'SF-2172', 'Blue Women''s Handbag', 'blue-womens-handbag', 1250000,
    'https://cdn.dummyjson.com/product-images/womens-bags/blue-women%27s-handbag/thumbnail.webp',
    'The Blue Women''s Handbag is a stylish and spacious accessory for everyday use. With a vibrant blue color and multiple compartments, it combines fashion and functionality.' FROM dual
  UNION ALL SELECT 2173, 212, 'SF-2173', 'Heshe Women''s Leather Bag', 'heshe-womens-leather-bag', 3250000,
    'https://cdn.dummyjson.com/product-images/womens-bags/heshe-women%27s-leather-bag/thumbnail.webp',
    'The Heshe Women''s Leather Bag is a luxurious and high-quality leather bag for the sophisticated woman. With a timeless design and durable craftsmanship, it''s a versatile accessory.' FROM dual
  UNION ALL SELECT 2174, 212, 'SF-2174', 'Prada Women Bag', 'prada-women-bag', 15000000,
    'https://cdn.dummyjson.com/product-images/womens-bags/prada-women-bag/thumbnail.webp',
    'The Prada Women Bag is an iconic designer bag that exudes elegance and luxury. Crafted with precision and featuring the Prada logo, it''s a statement piece for fashion enthusiasts.' FROM dual
  UNION ALL SELECT 2175, 212, 'SF-2175', 'White Faux Leather Backpack', 'white-faux-leather-backpack', 1000000,
    'https://cdn.dummyjson.com/product-images/womens-bags/white-faux-leather-backpack/thumbnail.webp',
    'The White Faux Leather Backpack is a trendy and practical backpack for the modern woman. With a sleek white design and ample storage space, it''s perfect for both casual and on-the-go styles.' FROM dual
  UNION ALL SELECT 2176, 212, 'SF-2176', 'Women Handbag Black', 'women-handbag-black', 1500000,
    'https://cdn.dummyjson.com/product-images/womens-bags/women-handbag-black/thumbnail.webp',
    'The Women Handbag in Black is a classic and versatile accessory that complements various outfits. With a timeless black color and functional design, it''s a must-have in every woman''s wardrobe.' FROM dual
  UNION ALL SELECT 2177, 211, 'SF-2177', 'Black Women''s Gown', 'black-womens-gown', 3250000,
    'https://cdn.dummyjson.com/product-images/womens-dresses/black-women%27s-gown/thumbnail.webp',
    'The Black Women''s Gown is an elegant and timeless evening gown. With a sleek black design, it''s perfect for formal events and special occasions, exuding sophistication and style.' FROM dual
  UNION ALL SELECT 2178, 211, 'SF-2178', 'Corset Leather With Skirt', 'corset-leather-with-skirt', 2250000,
    'https://cdn.dummyjson.com/product-images/womens-dresses/corset-leather-with-skirt/thumbnail.webp',
    'The Corset Leather With Skirt is a bold and edgy ensemble that combines a stylish corset with a matching skirt. Ideal for fashion-forward individuals, it makes a statement at any event.' FROM dual
  UNION ALL SELECT 2179, 211, 'SF-2179', 'Corset With Black Skirt', 'corset-with-black-skirt', 2000000,
    'https://cdn.dummyjson.com/product-images/womens-dresses/corset-with-black-skirt/thumbnail.webp',
    'The Corset With Black Skirt is a chic and versatile outfit that pairs a fashionable corset with a classic black skirt. It offers a trendy and coordinated look for various occasions.' FROM dual
  UNION ALL SELECT 2180, 211, 'SF-2180', 'Dress Pea', 'dress-pea', 1250000,
    'https://cdn.dummyjson.com/product-images/womens-dresses/dress-pea/thumbnail.webp',
    'The Dress Pea is a stylish and comfortable dress with a pea pattern. Perfect for casual outings, it adds a playful and fun element to your wardrobe, making it a great choice for day-to-day wear.' FROM dual
  UNION ALL SELECT 2181, 211, 'SF-2181', 'Marni Red & Black Suit', 'marni-red-and-black-suit', 4500000,
    'https://cdn.dummyjson.com/product-images/womens-dresses/marni-red-%26-black-suit/thumbnail.webp',
    'The Marni Red & Black Suit is a sophisticated and fashion-forward suit ensemble. With a combination of red and black tones, it showcases a modern design for a bold and confident look.' FROM dual
  UNION ALL SELECT 2182, 213, 'SF-2182', 'Green Crystal Earring', 'green-crystal-earring', 750000,
    'https://cdn.dummyjson.com/product-images/womens-jewellery/green-crystal-earring/thumbnail.webp',
    'The Green Crystal Earring is a dazzling accessory that features a vibrant green crystal. With a classic design, it adds a touch of elegance to your ensemble, perfect for formal or special occasions.' FROM dual
  UNION ALL SELECT 2183, 213, 'SF-2183', 'Green Oval Earring', 'green-oval-earring', 625000,
    'https://cdn.dummyjson.com/product-images/womens-jewellery/green-oval-earring/thumbnail.webp',
    'The Green Oval Earring is a stylish and versatile accessory with a unique oval shape. Whether for casual or dressy occasions, its green hue and contemporary design make it a standout piece.' FROM dual
  UNION ALL SELECT 2184, 213, 'SF-2184', 'Tropical Earring', 'tropical-earring', 500000,
    'https://cdn.dummyjson.com/product-images/womens-jewellery/tropical-earring/thumbnail.webp',
    'The Tropical Earring is a fun and playful accessory inspired by tropical elements. Featuring vibrant colors and a lively design, it''s perfect for adding a touch of summer to your look.' FROM dual
  UNION ALL SELECT 2185, 209, 'SF-2185', 'Black & Brown Slipper', 'black-and-brown-slipper', 500000,
    'https://cdn.dummyjson.com/product-images/womens-shoes/black-%26-brown-slipper/thumbnail.webp',
    'The Black & Brown Slipper is a comfortable and stylish choice for casual wear. Featuring a blend of black and brown colors, it adds a touch of sophistication to your relaxation.' FROM dual
  UNION ALL SELECT 2186, 209, 'SF-2186', 'Calvin Klein Heel Shoes', 'calvin-klein-heel-shoes', 2000000,
    'https://cdn.dummyjson.com/product-images/womens-shoes/calvin-klein-heel-shoes/thumbnail.webp',
    'Calvin Klein Heel Shoes are elegant and sophisticated, designed for formal occasions. With a classic design and high-quality materials, they complement your stylish ensemble.' FROM dual
  UNION ALL SELECT 2187, 209, 'SF-2187', 'Golden Shoes Woman', 'golden-shoes-woman', 1250000,
    'https://cdn.dummyjson.com/product-images/womens-shoes/golden-shoes-woman/thumbnail.webp',
    'The Golden Shoes for Women are a glamorous choice for special occasions. Featuring a golden hue and stylish design, they add a touch of luxury to your outfit.' FROM dual
  UNION ALL SELECT 2188, 209, 'SF-2188', 'Pampi Shoes', 'pampi-shoes', 750000,
    'https://cdn.dummyjson.com/product-images/womens-shoes/pampi-shoes/thumbnail.webp',
    'Pampi Shoes offer a blend of comfort and style for everyday use. With a versatile design, they are suitable for various casual occasions, providing a trendy and relaxed look.' FROM dual
  UNION ALL SELECT 2189, 209, 'SF-2189', 'Red Shoes', 'red-shoes', 875000,
    'https://cdn.dummyjson.com/product-images/womens-shoes/red-shoes/thumbnail.webp',
    'The Red Shoes make a bold statement with their vibrant red color. Whether for a party or a casual outing, these shoes add a pop of color and style to your wardrobe.' FROM dual
  UNION ALL SELECT 2190, 206, 'SF-2190', 'IWC Ingenieur Automatic Steel', 'iwc-ingenieur-automatic-steel', 125000000,
    'https://cdn.dummyjson.com/product-images/womens-watches/iwc-ingenieur-automatic-steel/thumbnail.webp',
    'The IWC Ingenieur Automatic Steel watch is a durable and sophisticated timepiece. With a stainless steel case and automatic movement, it combines precision and style for watch enthusiasts.' FROM dual
  UNION ALL SELECT 2191, 206, 'SF-2191', 'Rolex Cellini Moonphase Women', 'rolex-cellini-moonphase-women', 400000000,
    'https://cdn.dummyjson.com/product-images/womens-watches/rolex-cellini-moonphase/thumbnail.webp',
    'The Rolex Cellini Moonphase watch is a masterpiece of horology. Featuring a moon phase complication, it showcases the craftsmanship and elegance that Rolex is renowned for.' FROM dual
  UNION ALL SELECT 2192, 206, 'SF-2192', 'Rolex Datejust Women', 'rolex-datejust-women', 275000000,
    'https://cdn.dummyjson.com/product-images/womens-watches/rolex-datejust-women/thumbnail.webp',
    'The Rolex Datejust Women''s watch is an iconic timepiece designed for women. With a timeless design and a date complication, it offers both elegance and functionality.' FROM dual
  UNION ALL SELECT 2193, 206, 'SF-2193', 'Watch Gold for Women', 'watch-gold-for-women', 20000000,
    'https://cdn.dummyjson.com/product-images/womens-watches/watch-gold-for-women/thumbnail.webp',
    'The Gold Women''s Watch is a stunning accessory that combines luxury and style. Featuring a gold-plated case and a chic design, it adds a touch of glamour to any outfit.' FROM dual
  UNION ALL SELECT 2194, 206, 'SF-2194', 'Women''s Wrist Watch', 'womens-wrist-watch', 3250000,
    'https://cdn.dummyjson.com/product-images/womens-watches/women%27s-wrist-watch/thumbnail.webp',
    'The Women''s Wrist Watch is a versatile and fashionable timepiece for everyday wear. With a comfortable strap and a simple yet elegant design, it complements various styles.' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = 'ACTIVE', target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, 'ACTIVE', TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO product_inventory target
USING (
  SELECT 2001 product_id, 99 on_hand_quantity FROM dual
  UNION ALL SELECT 2002, 34 FROM dual
  UNION ALL SELECT 2003, 89 FROM dual
  UNION ALL SELECT 2004, 91 FROM dual
  UNION ALL SELECT 2005, 79 FROM dual
  UNION ALL SELECT 2006, 29 FROM dual
  UNION ALL SELECT 2007, 58 FROM dual
  UNION ALL SELECT 2008, 98 FROM dual
  UNION ALL SELECT 2009, 4 FROM dual
  UNION ALL SELECT 2010, 91 FROM dual
  UNION ALL SELECT 2011, 88 FROM dual
  UNION ALL SELECT 2012, 60 FROM dual
  UNION ALL SELECT 2013, 64 FROM dual
  UNION ALL SELECT 2014, 26 FROM dual
  UNION ALL SELECT 2015, 7 FROM dual
  UNION ALL SELECT 2016, 8 FROM dual
  UNION ALL SELECT 2017, 86 FROM dual
  UNION ALL SELECT 2018, 46 FROM dual
  UNION ALL SELECT 2019, 97 FROM dual
  UNION ALL SELECT 2020, 10 FROM dual
  UNION ALL SELECT 2021, 84 FROM dual
  UNION ALL SELECT 2022, 71 FROM dual
  UNION ALL SELECT 2023, 9 FROM dual
  UNION ALL SELECT 2024, 74 FROM dual
  UNION ALL SELECT 2025, 33 FROM dual
  UNION ALL SELECT 2026, 3 FROM dual
  UNION ALL SELECT 2027, 34 FROM dual
  UNION ALL SELECT 2028, 27 FROM dual
  UNION ALL SELECT 2029, 50 FROM dual
  UNION ALL SELECT 2030, 99 FROM dual
  UNION ALL SELECT 2031, 31 FROM dual
  UNION ALL SELECT 2032, 27 FROM dual
  UNION ALL SELECT 2033, 99 FROM dual
  UNION ALL SELECT 2034, 57 FROM dual
  UNION ALL SELECT 2035, 13 FROM dual
  UNION ALL SELECT 2036, 80 FROM dual
  UNION ALL SELECT 2037, 82 FROM dual
  UNION ALL SELECT 2038, 59 FROM dual
  UNION ALL SELECT 2039, 53 FROM dual
  UNION ALL SELECT 2040, 46 FROM dual
  UNION ALL SELECT 2041, 86 FROM dual
  UNION ALL SELECT 2042, 53 FROM dual
  UNION ALL SELECT 2043, 47 FROM dual
  UNION ALL SELECT 2044, 77 FROM dual
  UNION ALL SELECT 2045, 28 FROM dual
  UNION ALL SELECT 2046, 59 FROM dual
  UNION ALL SELECT 2047, 9 FROM dual
  UNION ALL SELECT 2048, 37 FROM dual
  UNION ALL SELECT 2049, 75 FROM dual
  UNION ALL SELECT 2050, 73 FROM dual
  UNION ALL SELECT 2051, 9 FROM dual
  UNION ALL SELECT 2052, 40 FROM dual
  UNION ALL SELECT 2053, 14 FROM dual
  UNION ALL SELECT 2054, 22 FROM dual
  UNION ALL SELECT 2055, 40 FROM dual
  UNION ALL SELECT 2056, 21 FROM dual
  UNION ALL SELECT 2057, 85 FROM dual
  UNION ALL SELECT 2058, 7 FROM dual
  UNION ALL SELECT 2059, 46 FROM dual
  UNION ALL SELECT 2060, 84 FROM dual
  UNION ALL SELECT 2061, 84 FROM dual
  UNION ALL SELECT 2062, 13 FROM dual
  UNION ALL SELECT 2063, 68 FROM dual
  UNION ALL SELECT 2064, 7 FROM dual
  UNION ALL SELECT 2065, 94 FROM dual
  UNION ALL SELECT 2066, 59 FROM dual
  UNION ALL SELECT 2067, 88 FROM dual
  UNION ALL SELECT 2068, 90 FROM dual
  UNION ALL SELECT 2069, 66 FROM dual
  UNION ALL SELECT 2070, 82 FROM dual
  UNION ALL SELECT 2071, 40 FROM dual
  UNION ALL SELECT 2072, 88 FROM dual
  UNION ALL SELECT 2073, 79 FROM dual
  UNION ALL SELECT 2074, 59 FROM dual
  UNION ALL SELECT 2075, 71 FROM dual
  UNION ALL SELECT 2076, 80 FROM dual
  UNION ALL SELECT 2077, 35 FROM dual
  UNION ALL SELECT 2078, 24 FROM dual
  UNION ALL SELECT 2079, 45 FROM dual
  UNION ALL SELECT 2080, 75 FROM dual
  UNION ALL SELECT 2081, 40 FROM dual
  UNION ALL SELECT 2082, 74 FROM dual
  UNION ALL SELECT 2083, 38 FROM dual
  UNION ALL SELECT 2084, 90 FROM dual
  UNION ALL SELECT 2085, 82 FROM dual
  UNION ALL SELECT 2086, 2 FROM dual
  UNION ALL SELECT 2087, 95 FROM dual
  UNION ALL SELECT 2088, 7 FROM dual
  UNION ALL SELECT 2089, 12 FROM dual
  UNION ALL SELECT 2090, 90 FROM dual
  UNION ALL SELECT 2091, 17 FROM dual
  UNION ALL SELECT 2092, 62 FROM dual
  UNION ALL SELECT 2093, 32 FROM dual
  UNION ALL SELECT 2094, 100 FROM dual
  UNION ALL SELECT 2095, 40 FROM dual
  UNION ALL SELECT 2096, 36 FROM dual
  UNION ALL SELECT 2097, 86 FROM dual
  UNION ALL SELECT 2098, 55 FROM dual
  UNION ALL SELECT 2099, 61 FROM dual
  UNION ALL SELECT 2100, 67 FROM dual
  UNION ALL SELECT 2101, 59 FROM dual
  UNION ALL SELECT 2102, 1 FROM dual
  UNION ALL SELECT 2103, 27 FROM dual
  UNION ALL SELECT 2104, 31 FROM dual
  UNION ALL SELECT 2105, 1 FROM dual
  UNION ALL SELECT 2106, 33 FROM dual
  UNION ALL SELECT 2107, 50 FROM dual
  UNION ALL SELECT 2108, 69 FROM dual
  UNION ALL SELECT 2109, 48 FROM dual
  UNION ALL SELECT 2110, 58 FROM dual
  UNION ALL SELECT 2111, 11 FROM dual
  UNION ALL SELECT 2112, 15 FROM dual
  UNION ALL SELECT 2118, 94 FROM dual
  UNION ALL SELECT 2119, 34 FROM dual
  UNION ALL SELECT 2120, 95 FROM dual
  UNION ALL SELECT 2121, 25 FROM dual
  UNION ALL SELECT 2122, 60 FROM dual
  UNION ALL SELECT 2123, 56 FROM dual
  UNION ALL SELECT 2124, 37 FROM dual
  UNION ALL SELECT 2125, 19 FROM dual
  UNION ALL SELECT 2126, 78 FROM dual
  UNION ALL SELECT 2127, 55 FROM dual
  UNION ALL SELECT 2128, 48 FROM dual
  UNION ALL SELECT 2129, 12 FROM dual
  UNION ALL SELECT 2130, 80 FROM dual
  UNION ALL SELECT 2131, 67 FROM dual
  UNION ALL SELECT 2132, 0 FROM dual
  UNION ALL SELECT 2133, 19 FROM dual
  UNION ALL SELECT 2134, 50 FROM dual
  UNION ALL SELECT 2135, 82 FROM dual
  UNION ALL SELECT 2136, 7 FROM dual
  UNION ALL SELECT 2137, 53 FROM dual
  UNION ALL SELECT 2138, 100 FROM dual
  UNION ALL SELECT 2139, 22 FROM dual
  UNION ALL SELECT 2140, 75 FROM dual
  UNION ALL SELECT 2141, 43 FROM dual
  UNION ALL SELECT 2142, 30 FROM dual
  UNION ALL SELECT 2143, 98 FROM dual
  UNION ALL SELECT 2144, 10 FROM dual
  UNION ALL SELECT 2145, 25 FROM dual
  UNION ALL SELECT 2146, 95 FROM dual
  UNION ALL SELECT 2147, 96 FROM dual
  UNION ALL SELECT 2148, 84 FROM dual
  UNION ALL SELECT 2149, 90 FROM dual
  UNION ALL SELECT 2150, 16 FROM dual
  UNION ALL SELECT 2151, 28 FROM dual
  UNION ALL SELECT 2152, 6 FROM dual
  UNION ALL SELECT 2153, 0 FROM dual
  UNION ALL SELECT 2154, 60 FROM dual
  UNION ALL SELECT 2155, 1 FROM dual
  UNION ALL SELECT 2156, 24 FROM dual
  UNION ALL SELECT 2157, 86 FROM dual
  UNION ALL SELECT 2158, 27 FROM dual
  UNION ALL SELECT 2159, 47 FROM dual
  UNION ALL SELECT 2160, 62 FROM dual
  UNION ALL SELECT 2161, 92 FROM dual
  UNION ALL SELECT 2162, 52 FROM dual
  UNION ALL SELECT 2163, 43 FROM dual
  UNION ALL SELECT 2164, 55 FROM dual
  UNION ALL SELECT 2165, 22 FROM dual
  UNION ALL SELECT 2166, 73 FROM dual
  UNION ALL SELECT 2172, 76 FROM dual
  UNION ALL SELECT 2173, 99 FROM dual
  UNION ALL SELECT 2174, 75 FROM dual
  UNION ALL SELECT 2175, 39 FROM dual
  UNION ALL SELECT 2176, 11 FROM dual
  UNION ALL SELECT 2177, 25 FROM dual
  UNION ALL SELECT 2178, 30 FROM dual
  UNION ALL SELECT 2179, 33 FROM dual
  UNION ALL SELECT 2180, 6 FROM dual
  UNION ALL SELECT 2181, 62 FROM dual
  UNION ALL SELECT 2182, 54 FROM dual
  UNION ALL SELECT 2183, 73 FROM dual
  UNION ALL SELECT 2184, 1 FROM dual
  UNION ALL SELECT 2185, 3 FROM dual
  UNION ALL SELECT 2186, 93 FROM dual
  UNION ALL SELECT 2187, 88 FROM dual
  UNION ALL SELECT 2188, 49 FROM dual
  UNION ALL SELECT 2189, 7 FROM dual
  UNION ALL SELECT 2190, 90 FROM dual
  UNION ALL SELECT 2191, 52 FROM dual
  UNION ALL SELECT 2192, 4 FROM dual
  UNION ALL SELECT 2193, 0 FROM dual
  UNION ALL SELECT 2194, 12 FROM dual
) source
ON (target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
VALUES
  (source.product_id, source.on_hand_quantity, 0, 0, TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));
