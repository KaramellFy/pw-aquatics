-- Contact form messages (docs/DATABASE.md §3.8).
CREATE TABLE contact_messages (
    id            uuid PRIMARY KEY,
    name          varchar(150)  NOT NULL,
    email         citext        NOT NULL,
    subject       varchar(200)  NOT NULL,
    message       varchar(5000) NOT NULL,
    order_number  varchar(20),
    status        varchar(20)   NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'READ', 'ANSWERED', 'SPAM')),
    created_at    timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_contact_messages_status ON contact_messages (status, created_at DESC);
