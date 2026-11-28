package com.example.demo.documentation;

import com.example.demo.catalog.search.ProductSearchSort;
import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.entity.OrderStatus;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI applicationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Backend SWE API")
                .version("v1")
                .description("Private university course project. Log in using /api/auth/login, copy the JWT "
                        + "from the Authorization response header, and enter it in Authorize without the Bearer prefix."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer applicationDocumentation() {
        return openApi -> {
            openApi.getPaths().forEach((path, item) -> {
                boolean authenticatedPath = path.equals("/api/me") || path.startsWith("/api/me/")
                        || path.startsWith("/api/users/") || path.startsWith("/api/admin/")
                        || path.equals("/api/auth/password") || path.startsWith("/api/image-uploads");
                item.readOperationsMap().forEach((method, operation) -> {
                    boolean catalogWrite = path.startsWith("/api/products/") && method != PathItem.HttpMethod.GET;
                    if ((authenticatedPath || catalogWrite) && operation.getSecurity() == null) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                    }
                });
            });
            documentQueries(openApi);
            PathItem login = openApi.getPaths().get("/api/auth/login");
            if (login != null && login.getPost() != null && login.getPost().getResponses().get("200") != null) {
                login.getPost().getResponses().get("200").addHeaderObject("Authorization", new Header()
                        .description("Bearer JWT. Copy the token into Authorize without the Bearer prefix.")
                        .schema(new StringSchema()));
            }
        };
    }

    private void documentQueries(OpenAPI openApi) {
        PathItem filterOptions = openApi.getPaths().get("/api/product-filter-options");
        if (filterOptions != null && filterOptions.getGet() != null) {
            filterOptions.getGet().setParameters(List.of(
                    query("categoryId", new StringSchema().format("uuid"), "Optional category identifier; includes descendants. Omit for global options."),
                    query("brandId", new ArraySchema().items(new StringSchema().format("uuid")).maxItems(20),
                            "Optional brand identifiers, sent as repeated brandId parameters.")
                            .style(Parameter.StyleEnum.FORM).explode(true)));
        }
        PathItem catalog = openApi.getPaths().get("/api/products");
        if (catalog != null && catalog.getGet() != null) {
            catalog.getGet().setParameters(List.of(page(), size(),
                    query("q", new StringSchema(), "Search text; required for relevance sorting."),
                    query("categoryId", new StringSchema().format("uuid"), "Category identifier."),
                    query("brandId", new ArraySchema().items(new StringSchema().format("uuid")).maxItems(20),
                            "Brand identifiers, sent as repeated brandId query parameters.")
                            .style(Parameter.StyleEnum.FORM).explode(true),
                    query("minPrice", new StringSchema().pattern("^(0|[1-9][0-9]{0,29})$"), "Minimum whole VND price."),
                    query("maxPrice", new StringSchema().pattern("^(0|[1-9][0-9]{0,29})$"), "Maximum whole VND price."),
                    query("sort", new StringSchema()._enum(Arrays.stream(ProductSearchSort.values())
                            .map(ProductSearchSort::getParameterValue).toList()), "Supported catalog sort."),
                    query("specFilters", new StringSchema().example("[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]},{\"key\":\"cpu_family\",\"alternatives\":[{\"op\":\"eq\",\"value\":\"core i5\"}]}]"), "JSON specification filter groups across all categories; categoryId is optional. Keys: physical_ram (GB), installed_storage_gb (GB), cpu_family (text), display_diagonal (inch), display_resolution (text). Get selectable values from /api/product-filter-options. Alternatives within a group use OR; groups use AND on the same variant.")));
        }
        PathItem orders = openApi.getPaths().get("/api/me/orders");
        if (orders != null && orders.getGet() != null) {
            orders.getGet().setParameters(List.of(page(), size(),
                    query("status", new StringSchema()._enum(Arrays.stream(OrderStatus.values()).map(Enum::name).toList()),
                            "Optional order status."),
                    query("sort", new StringSchema()._enum(OrderListRequest.SORTS.stream().sorted().toList())
                            ._default("placedAt,desc"), "Order sort, followed by ID for stable pagination.")));
        }
        PathItem reviews = openApi.getPaths().get("/api/products/{productId}/reviews");
        if (reviews != null && reviews.getGet() != null) {
            List<Parameter> parameters = pathParameters(reviews.getGet());
            parameters.addAll(List.of(page(), size(),
                    query("sort", new StringSchema()._enum(List.of("createdAt,asc", "createdAt,desc", "rating,asc", "rating,desc"))
                            ._default("createdAt,desc"), "Review sort."),
                    query("rating", new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(5)), "Exact star rating."),
                    query("hasImages", new BooleanSchema(), "Filter by attached review images."),
                    query("hasComment", new BooleanSchema(), "Filter by a nonempty comment.")));
            reviews.getGet().setParameters(parameters);
        }
        PathItem wishlist = openApi.getPaths().get("/api/me/wishlist");
        if (wishlist != null && wishlist.getGet() != null) {
            wishlist.getGet().setParameters(List.of(page(), size()));
        }
        PathItem wishlistItem = openApi.getPaths().get("/api/me/wishlist/{productId}");
        if (wishlistItem != null) {
            wishlistItem.readOperations().forEach(operation -> {
                operation.setParameters(pathParameters(operation));
                operation.setRequestBody(null);
            });
        }
    }

    private List<Parameter> pathParameters(Operation operation) {
        List<Parameter> parameters = new ArrayList<>();
        if (operation.getParameters() != null) {
            operation.getParameters().stream().filter(parameter -> "path".equals(parameter.getIn()))
                    .forEach(parameters::add);
        }
        return parameters;
    }

    private Parameter page() {
        return query("page", new IntegerSchema().minimum(BigDecimal.ZERO)._default(0), "Zero-based page number.");
    }

    private Parameter size() {
        return query("size", new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(100))._default(20),
                "Page size, between 1 and 100.");
    }

    private Parameter query(String name, Schema<?> schema, String description) {
        return new Parameter().name(name).in("query").required(false).schema(schema).description(description);
    }
}
