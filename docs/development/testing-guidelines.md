## Testing guidelines:
* All tests are done with use of kotest
* **Integration tests** are the most important tests. 
    * Use `BaseIntegrationTest` for integration tests requiring a database.
    * It automatically starts a MongoDB instance via **Testcontainers**.
    * Integration tests should verify full business flows by interacting with multiple services and publishing/listening to events.
* **Unit tests** are also important but only when testing some quirky internal logic,
  or when you feel you wouldn't be able to sleep well at night without them
* IF you decide to write unit tests, do not try to try create huge context around
  them; just simple mocks and responses; avoid examples based on some specific business scenarios