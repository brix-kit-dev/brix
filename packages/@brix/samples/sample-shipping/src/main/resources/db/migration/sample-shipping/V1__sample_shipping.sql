-- sample-shipping's business table. The inbox (sample_shipping_inbox) is not created here: the platform creates it
-- from its versioned messaging migrations because the manifest declares data.inbox.
CREATE TABLE sample_shipping_shipment (
    id             BIGINT       NOT NULL,
    tenant_id      BIGINT       NOT NULL,
    order_id       VARCHAR(32)  NOT NULL,
    sku            VARCHAR(64)  NOT NULL,
    quantity       INTEGER      NOT NULL,
    order_version  BIGINT       NOT NULL,
    prepared_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_sample_shipping_shipment PRIMARY KEY (id),
    CONSTRAINT uk_sample_shipping_shipment_order UNIQUE (tenant_id, order_id)
);
