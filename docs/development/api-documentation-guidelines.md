## API Documentation Guidelines

For any module that exposes REST endpoints, we maintain comprehensive documentation using both `.http` files and OpenAPI annotations.

### 1. IntelliJ HTTP Client Files (`.http`)
*   **Location:** Store `.http` files in the `docs/http` directory of the respective module.
*   **Completeness:** Every Controller must be supplemented with `.http` files that explore its full functionality.
*   **Example Requests:** Provide realistic example requests with all necessary headers and body parameters.
*   **Expected Responses:** Use comments to describe expected response codes and sample response bodies.
*   **Environment Variables:** Use `{{host}}` and other variables to make the files usable across different environments.
*   **Source of Truth:** The `.http` files are the primary source of truth for the API behavior and documentation.

### 2. OpenAPI Documentation
*   **Annotations:** Use `springdoc-openapi` annotations in Controller classes and methods.
    *   `@Tag`: Use to group endpoints logically.
    *   `@Operation`: Provide a clear summary and description (matching the `.http` file).
    *   `@ApiResponse`: Document success and common error responses (400, 404, 409, etc.).
    *   `@Parameter`: Document path variables and request parameters.
*   **DTO Documentation:** Ensure DTOs used in requests and responses are correctly annotated if necessary (though field names and types are often sufficient).

### 3. Automated Metadata (OpenApiConfig)
*   **Security:** Do not manually document JWT security in OpenAPI annotations. The `OpenApiConfig`'s `OperationCustomizer` automatically detects if a path is unsecured or requires JWT.
*   **Roles:** Do not manually document required roles in descriptions. The `OperationCustomizer` automatically appends "**Required Role:** `...`" to the operation description based on the `@Privileged` annotation.

### 4. Synchronization
*   Any change to the API (adding endpoints, changing parameters, updating response structures) **MUST** be reflected in both the `.http` files and the OpenAPI annotations simultaneously.
