CREATE TABLE processed_kafka_events(
    event_id      UUID         NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_processed_kafka_events PRIMARY KEY (event_id, consumer_name)
);
