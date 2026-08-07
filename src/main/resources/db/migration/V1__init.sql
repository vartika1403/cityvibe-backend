-- Initial schema for CityVibe.
-- Mirrors the Event entity (com.cityvibe.model.Event) exactly so that
-- Hibernate's ddl-auto=validate passes against this table.
CREATE TABLE IF NOT EXISTS events (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    category      VARCHAR(255) NOT NULL,
    date_time     VARCHAR(255) DEFAULT NULL,
    description   TEXT,
    duration      VARCHAR(255) DEFAULT NULL,
    image_url     VARCHAR(1000) DEFAULT NULL,
    organizer     VARCHAR(255) DEFAULT NULL,
    price         VARCHAR(255) DEFAULT NULL,
    title         VARCHAR(255) NOT NULL,
    venue_address VARCHAR(255) DEFAULT NULL,
    venue_name    VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
