package com.example.demo.catalog.repository;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties="spring.datasource.url=jdbc:tc:postgresql:17-alpine:///search_calibration")
@ActiveProfiles("test")
class FuzzyCalibrationIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Test void calibration_shouldMeasureLabeledFixturesAndExhaustiveSourceTokenRecall()throws Exception{
        var json=JsonMapper.builder().build();
        var pairs=json.readTree(getClass().getResourceAsStream("/catalog/search/fuzzy-pairs-v1.json"));
        var corpus=json.readTree(getClass().getResourceAsStream("/catalog/search/source-titles-v1.json"));
        var titles=new ArrayList<String>();corpus.get("titles").forEach(t->titles.add(t.asString()));
        var scores=new ArrayList<Map<String,Object>>();
        for(var p:pairs){double score=jdbc.queryForObject("SELECT similarity(?,?)",Double.class,p.get("q").asString(),p.get("c").asString());scores.add(Map.of("query",p.get("q").asString(),"candidate",p.get("c").asString(),"positive",p.get("label").asBoolean(),"similarity",score,"typoAllowed",jdbc.queryForObject("SELECT catalog_search_typo_v1(?,?)",Boolean.class,p.get("q").asString(),p.get("c").asString())));}
        var grid=new ArrayList<Map<String,Object>>();
        for(double threshold:new double[]{.15,.20,.25,.30,.32,.33,.35,.40,.45,.50}){
            long tp=scores.stream().filter(p->(boolean)p.get("positive")&&(double)p.get("similarity")>threshold&&(boolean)p.get("typoAllowed")).count();
            long fp=scores.stream().filter(p->!(boolean)p.get("positive")&&(double)p.get("similarity")>threshold&&(boolean)p.get("typoAllowed")).count();
            long fn=15-tp;grid.add(Map.of("threshold",threshold,"tp",tp,"fp",fp,"fn",fn,"precision",tp/(double)(tp+fp),"recall",tp/15.0));
        }
        var oracle=tx.execute(status->{
            jdbc.execute("CREATE TEMP TABLE source_words ON COMMIT DROP AS SELECT DISTINCT token FROM unnest(ARRAY[]::text[]) t(token)");
            for(String title:titles)jdbc.update("INSERT INTO source_words SELECT token FROM catalog_search_tokens_v1(catalog_search_normalize_v1(?)) WHERE NOT structurally_protected",title);
            jdbc.execute("CREATE TEMP TABLE natural_words ON COMMIT DROP AS SELECT DISTINCT token FROM source_words WHERE char_length(token)>=4");
            jdbc.queryForObject("SELECT set_config('pg_trgm.strict_word_similarity_threshold','0.32',true)",String.class);
            var counts=new ArrayList<Map<String,Object>>();
            for(var p:scores){String query=(String)p.get("query");
                var result=jdbc.queryForMap("SELECT count(*) FILTER(WHERE similarity(?,token)>0.32 AND catalog_search_typo_v1(?,token)) AS oracle,count(*) FILTER(WHERE similarity(?,token)>0.32 AND catalog_search_typo_v1(?,token) AND NOT(? <<% token)) AS lost FROM natural_words",query,query,query,query,query);
                assertThat(((Number)result.get("lost")).longValue()).as(query).isZero();counts.add(Map.of("query",query,"oracleMatches",result.get("oracle"),"prefilterLost",result.get("lost")));
            }return counts;
        });
        var output=Path.of("target","search-calibration");Files.createDirectories(output);
        Files.writeString(output.resolve("results.json"),json.writeValueAsString(Map.of("selectedThreshold",.32,"sourceSha256",corpus.get("sourceSha256").asString(),"sourceTitles",titles.size(),"scores",scores,"grid",grid,"exhaustivePrefilter",oracle,"manualSourceJudgments",0)));
        assertThat(grid.stream().filter(x->x.get("threshold").equals(.32)).findFirst().orElseThrow().get("fn")).isEqualTo(0L);
    }
}
