-- Airbus Inventory Management System - seed data
-- Runs on every startup right after schema.sql (spring.sql.init.mode=always).

-- Demo users (passwords are bcrypt hashes, generated with cost factor 10)
-- admin   / admin123    (role ADMIN)
-- manager / manager123  (role USER)
INSERT INTO users (username, password, role) VALUES
    ('admin', '$2y$10$VkSlQ8lsLVyW62cdnz4dTe1jamFMjfXv9UZbvxCpGlgGKcwV7GLGO', 'ADMIN'),
    ('manager', '$2y$10$1VWbB9TyLuEpDcUe8iMb8.ktL7KivhcpjXFVe7LtXvw3YVZAWVeX.', 'USER');

-- Avionics
INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level) VALUES
    ('Flight Management Computer (FMC)', 'Avionics', 18, 42500.00, 'Honeywell Aerospace', 5),
    ('Weather Radar Antenna', 'Avionics', 24, 15800.00, 'Collins Aerospace', 6),
    ('Autopilot Flight Director System', 'Avionics', 12, 38900.00, 'Thales Group', 4),
    ('VHF Communication Transceiver', 'Avionics', 40, 6200.00, 'Rockwell Collins', 10),
    ('Air Data Inertial Reference Unit (ADIRU)', 'Avionics', 9, 51200.00, 'Honeywell Aerospace', 3),
    ('Traffic Collision Avoidance System Processor', 'Avionics', 15, 27600.00, 'Honeywell Aerospace', 5),
    ('Multi-Function Display Unit', 'Avionics', 30, 11400.00, 'Thales Group', 8),
    ('GPS/GNSS Navigation Receiver', 'Avionics', 45, 4300.00, 'Garmin Aerospace', 12);

-- Landing Gear
INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level) VALUES
    ('Main Landing Gear Strut Assembly', 'Landing Gear', 6, 185000.00, 'Safran Landing Systems', 2),
    ('Nose Landing Gear Actuator', 'Landing Gear', 14, 22300.00, 'Liebherr Aerospace', 4),
    ('Landing Gear Wheel Hub Assembly', 'Landing Gear', 22, 8700.00, 'Safran Landing Systems', 6),
    ('Brake Control Unit', 'Landing Gear', 16, 19800.00, 'Meggitt PLC', 5),
    ('Carbon Brake Disc Stack', 'Landing Gear', 28, 13500.00, 'Honeywell Aerospace', 8),
    ('Landing Gear Position Sensor', 'Landing Gear', 50, 1450.00, 'Safran Landing Systems', 15),
    ('Shock Absorber Strut Seal Kit', 'Landing Gear', 60, 340.00, 'Liebherr Aerospace', 20),
    ('Steering Control Valve', 'Landing Gear', 11, 9600.00, 'Safran Landing Systems', 4);

-- Engine Components
INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level) VALUES
    ('High-Pressure Turbine Blade', 'Engine Components', 120, 2800.00, 'CFM International', 30),
    ('Fan Blade Assembly', 'Engine Components', 34, 17600.00, 'Safran Aircraft Engines', 10),
    ('Combustion Chamber Liner', 'Engine Components', 8, 64000.00, 'CFM International', 3),
    ('Fuel Injector Nozzle', 'Engine Components', 75, 1250.00, 'Woodward Inc.', 20),
    ('Engine Fire Detection Sensor', 'Engine Components', 42, 3100.00, 'Meggitt PLC', 12),
    ('Oil Pump Assembly', 'Engine Components', 19, 8900.00, 'Safran Aircraft Engines', 6),
    ('Turbine Disc', 'Engine Components', 5, 98000.00, 'CFM International', 2),
    ('Engine Bleed Air Valve', 'Engine Components', 23, 6700.00, 'Honeywell Aerospace', 7);

-- Cabin Interiors
INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level) VALUES
    ('Passenger Seat Assembly - Economy', 'Cabin Interiors', 96, 2650.00, 'Recaro Aircraft Seating', 24),
    ('Overhead Storage Bin', 'Cabin Interiors', 54, 1890.00, 'Diehl Aviation', 15),
    ('Galley Cart', 'Cabin Interiors', 40, 780.00, 'Driessen Aerospace', 12),
    ('Lavatory Module', 'Cabin Interiors', 10, 24500.00, 'Jamco Corporation', 3),
    ('Cabin LED Lighting Strip', 'Cabin Interiors', 130, 210.00, 'Diehl Aviation', 30),
    ('Passenger Service Unit (PSU)', 'Cabin Interiors', 68, 950.00, 'Safran Cabin', 18),
    ('Emergency Oxygen Mask Module', 'Cabin Interiors', 85, 640.00, 'B/E Aerospace', 25);

-- Hydraulics
INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level) VALUES
    ('Hydraulic Pump Assembly', 'Hydraulics', 17, 21400.00, 'Parker Aerospace', 5),
    ('Hydraulic Reservoir', 'Hydraulics', 20, 4300.00, 'Parker Aerospace', 6),
    ('Hydraulic Actuator - Flight Control', 'Hydraulics', 13, 31200.00, 'Moog Inc.', 4),
    ('Hydraulic Filter Element', 'Hydraulics', 88, 320.00, 'Eaton Aerospace', 25),
    ('Pressure Relief Valve', 'Hydraulics', 36, 1650.00, 'Parker Aerospace', 10),
    ('Hydraulic Hose Assembly', 'Hydraulics', 70, 480.00, 'Eaton Aerospace', 20),
    ('Servo Valve', 'Hydraulics', 21, 9200.00, 'Moog Inc.', 6);
