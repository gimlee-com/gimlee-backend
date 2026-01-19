db['gimlee-payments-exchange-rates'].createIndex(
    { "bc": 1, "qc": 1, "ua": -1 },
    { name: "idx_bc_qc_ua" }
);
