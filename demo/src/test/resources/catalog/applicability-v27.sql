            registry_roots AS (
                SELECT catalog_search_safe_normalize_v2(name) AS family,min(id::text)::uuid AS id FROM categories
                WHERE catalog_search_safe_normalize_v2(name) IN ('laptop','tablet')
                GROUP BY catalog_search_safe_normalize_v2(name) HAVING count(*)=1
            ), registry_categories(family,id) AS (
                SELECT family,id FROM registry_roots UNION
                SELECT r.family,c.id FROM registry_categories r JOIN categories c ON c.parent_category_id=r.id
                WHERE catalog_search_safe_normalize_v2(c.name)=r.family OR catalog_search_safe_normalize_v2(c.name) NOT IN ('laptop','tablet','smartphone','computer accessories','headphone & earbuds','charger & power bank','speaker','phone accessories','smartwatch')
            )
