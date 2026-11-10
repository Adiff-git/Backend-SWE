# Final executed regression union

{'tests': 705, 'failures': 0, 'errors': 0, 'skipped': 0}

342 unit/MVC +363 integration tests,0 failures/errors/skips,705 unique executed cases in latest per-suite reports. The final full functional run and timing job together cover every discovered suite;latest per-suite XML is counted once. Repeated suite executions are not double-counted. Source-test discovery coverage is checked by collect-mvp-evidence.py. See compressed actual logs/XML hashes and test-results-final.json for per-suite/per-method evidence.

```bash
# From Backend-SWE;Java25,Docker,existing Maven dependencies.
mvn -o -f demo/pom.xml '-Dit.test=*IT,!*PerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify
mvn -o -f demo/pom.xml -Dit.test=ProductSearchRepositoryIT,ProductSearchControllerIT,ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
# Final actual1500 HTTP measurement (also records exact source plans):
mvn -o -f demo/pom.xml -Dit.test=ProductSourceWorkloadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
# Approved tooling,isolated Python environment:
python3 -m venv /tmp/search-openapi-repro
/tmp/search-openapi-repro/bin/pip install -r specs/007-product-search-filter/evidence/t006-validator-freeze.txt
/tmp/search-openapi-repro/bin/python specs/007-product-search-filter/evidence/probe-openapi-capabilities.py --approved-tools
python3 specs/007-product-search-filter/evidence/collect-mvp-evidence.py
```
