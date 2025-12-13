
INSERT INTO roles (name, description)
VALUES ('ROLE_USER', 'Vai trò người dùng cơ bản'),
       ('ROLE_SELLER', 'Vai trò người bán hàng/chủ shop'),
       ('ROLE_ADMIN', 'Vai trò quản trị viên'),
       ('ROLE_SUPERADMIN', 'Vai trò quản trị viên cấp cao');

INSERT INTO users (username, password, email, phone, first_name, last_name, birth_date, avatar, gender, enabled, account_status, created_at, updated_at)
VALUES ('bangtran', '$2a$10$tFd/sNlZuPL.FNL898ihnuSkNl4y9YbDVi1qtA7btiTkgrpkbbZjq', 'bangtran08@vsvshop.com', '0334236824', 'Trần', 'Văn Bằng', '1990-01-01', 'https://cdn-icons-png.flaticon.com/512/3607/3607444.png', 'MALE', TRUE, 'ACTIVE', NOW(), NOW());




INSERT INTO user_role(user_id, role_id)
VALUES
    (1, 1), (1, 2), (1, 3), (1, 4);





INSERT INTO carts (user_id)
VALUES (1);

INSERT INTO categories (name, level)
VALUES
    ('Pants', 1),
    ('Shirts', 1),
    ('Shoes', 1),
    ('Accessories', 1);

-- Level 2
INSERT INTO categories (name, level, parent_category_id)
VALUES
    -- Pants
    ('Jeans', 2, 1),
    ('Trousers', 2, 1),
    ('Shorts', 2, 1),
    ('Joggers', 2, 1),
    ('Chinos', 2, 1),

    -- Shirts
    ('T-Shirts', 2, 2),
    ('Dress Shirts', 2, 2),
    ('Jackets', 2, 2),
    ('Polos', 2, 2),
    ('Sweaters', 2, 2),

    -- Shoes
    ('Sneakers', 2, 3),
    ('Formal Shoes', 2, 3),
    ('Loafers', 2, 3),
    ('Boots', 2, 3),
    ('Sandals', 2, 3),

    -- Accessories
    ('Hats', 2, 4),
    ('Bags', 2, 4),
    ('Belts', 2, 4),
    ('Sunglasses', 2, 4),
    ('Jewelry', 2, 4);

-- Level 3
INSERT INTO categories (name, level, parent_category_id)
VALUES
    -- Jeans
    ('Skinny Jeans', 3, 5),
    ('Ripped Jeans', 3, 5),
    ('Straight Jeans', 3, 5),
    ('High-Waist Jeans', 3, 5),
    ('Wide-Leg Jeans', 3, 5),

    -- Trousers
    ('Slim Fit Trousers', 3, 6),
    ('Straight Fit Trousers', 3, 6),
    ('High-Waist Trousers', 3, 6),
    ('Classic Fit Trousers', 3, 6),
    ('Plain Trousers', 3, 6),

    -- T-Shirts
    ('Crew Neck T-Shirt', 3, 11),
    ('V-Neck T-Shirt', 3, 11),
    ('Oversized T-Shirt', 3, 11),
    ('Half Sleeve T-Shirt', 3, 11),
    ('Graphic T-Shirt', 3, 11),

    -- Sneakers
    ('Running Shoes', 3, 16),
    ('White Sneakers', 3, 16),
    ('High-Top Sneakers', 3, 16),
    ('Canvas Sneakers', 3, 16),
    ('Training Shoes', 3, 16),

    -- Bags
    ('Tote Bag', 3, 21),
    ('Crossbody Bag', 3, 21),
    ('Handbag', 3, 21),
    ('Mini Bag', 3, 21),
    ('Backpack', 3, 21);




INSERT INTO coupons (code, discount_percentage, start_date, end_date, min_order_value, is_active)
VALUES
    -- Mã còn hiệu lực, không yêu cầu giá trị tối thiểu
    ('GIAM10', 10.00, CURRENT_DATE - INTERVAL '1 day', CURRENT_DATE + INTERVAL '7 day', 0, TRUE),
    -- Mã đã hết hạn
    ('SALEHETHAN', 20.00, '2023-01-01', '2023-01-31', 0, TRUE),
    -- Mã chưa có hiệu lực
    ('SAPTOI', 15.00, CURRENT_DATE + INTERVAL '7 day', CURRENT_DATE + INTERVAL '14 day', 0, TRUE),
    -- Mã yêu cầu giá trị đơn hàng tối thiểu 500k
    ('GIAM50K', 10.00, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 day', 500000, TRUE),
    -- Mã không hoạt động
    ('KHOA', 50.00, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 day', 0, FALSE);
