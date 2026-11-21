package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.model.Category;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.Supplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// COM-21 replicates the POS search on purpose: both run the table of contracts/product-search-cases.json, which the
// frontend's product-search.contract.spec.ts also runs, so the two cannot drift apart
class ProductTextMatcherContractTest {

    private static final Path CASES = Path.of("..", "contracts", "product-search-cases.json");

    static Stream<Arguments> cases() throws IOException {
        JsonNode table = JsonMapper.builder().build().readTree(Files.readString(CASES));
        List<Product> products = new ArrayList<>();
        for (JsonNode node : table.get("products")) {
            products.add(Product.builder().id(UUID.randomUUID())
                .code(node.get("code").asString())
                .name(node.get("name").asString())
                .supplier(node.get("supplierName").isNull() ? null
                    : Supplier.builder().name(node.get("supplierName").asString()).build())
                .category(node.get("categoryName").isNull() ? null
                    : Category.builder().name(node.get("categoryName").asString()).build())
                .build());
        }
        List<Arguments> cases = new ArrayList<>();
        for (JsonNode node : table.get("cases")) {
            List<String> expected = new ArrayList<>();
            node.get("expected").forEach(code -> expected.add(code.asString()));
            cases.add(Arguments.of(node.get("about").asString(), node.get("term").asString(), expected, products));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}: «{1}»")
    @MethodSource("cases")
    void searchMatchesTheSharedTable(String about, String term, List<String> expected, List<Product> products) {
        List<String> codes = ProductTextMatcher.search(term, products).stream()
            .map(match -> match.product().getCode())
            .toList();

        assertThat(codes).as(about).isEqualTo(expected);
    }
}
