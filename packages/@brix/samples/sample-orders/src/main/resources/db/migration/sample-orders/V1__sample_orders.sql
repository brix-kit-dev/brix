-- sample-orders' business table. The outbox (sample_orders_outbox) is not created here: the platform creates it
-- from its versioned messaging migrations because the manifest declares data.outbox.
CREATE TABLE sample_orders_order (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    sku         VARCHAR(64)  NOT NULL,
    quantity    INTEGER      NOT NULL,
    version     BIGINT       NOT NULL,
    placed_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_sample_orders_order PRIMARY KEY (id),
    CONSTRAINT chk_sample_orders_order_quantity CHECK (quantity BETWEEN 1 AND 1000)
);

CREATE INDEX idx_sample_orders_order_tenant ON sample_orders_order (tenant_id, placed_at);
