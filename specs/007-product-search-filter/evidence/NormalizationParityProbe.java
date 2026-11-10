// Context-free T013 evidence probe; not an application component or test of approved vocabulary.
import com.example.demo.catalog.search.ProductSearchNormalizer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class NormalizationParityProbe {
    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        ProductSearchNormalizer normalizer = new ProductSearchNormalizer();
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode value : json.readTree(Path.of(args[0]).toFile())) {
            String input = value.isNull() ? null : value.asString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("input", input);
            try {
                row.put("normalized", normalizer.normalize(input));
                row.put("tokens", normalizer.tokenize(input).stream().map(token ->
                        Map.of("text", token.getText(), "protected", token.isStructurallyProtected())).toList());
                row.put("accepted", true);
            } catch (com.example.demo.catalog.exception.InvalidCatalogRequestException exception) {
                row.put("accepted", false);
            }
            results.add(row);
        }
        System.out.print(json.writeValueAsString(results));
    }
}
