package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryPatch;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Query;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Binding;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryPage;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryDetail;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryMutation;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CategoryRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import com.example.demo.catalog.repository.CatalogTaxonomyQueries;
import com.example.demo.catalog.repository.CatalogTaxonomyQueries.BindingScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminCategoryServiceImpl implements AdminCategoryService {
    private final CategoryRepository categories;
    private final CatalogTaxonomyQueries queries;
    private final CatalogMutationRepository revisions;
    private final CatalogAdminAggregateReader aggregates;
    private final AdminCatalogConverter converter;
    private final TransactionTemplate transactions;
    public AdminCategoryServiceImpl(CategoryRepository categories,CatalogTaxonomyQueries queries,CatalogMutationRepository revisions,CatalogAdminAggregateReader aggregates,AdminCatalogConverter converter,PlatformTransactionManager manager){
        this.categories=categories;this.queries=queries;this.revisions=revisions;this.aggregates=aggregates;this.converter=converter;
        transactions=new TransactionTemplate(manager);transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Override public CategoryPage list(Query query){return queries.categories(query);}
    @Override public CategoryDetail get(UUID id){CatalogAggregateMutation.requireCompatibleTransaction();return transactions.execute(status->{long taxonomy=revisions.lockTaxonomy(false);revisions.lockVersions(Resource.CATEGORY,List.of(id),false);return new CategoryDetail(view(fresh(id)),Long.toString(taxonomy));});}
    @Override public CategoryMutation create(CategoryCreate request){
        CatalogAggregateMutation.requireCompatibleTransaction();return transactions.execute(status->{
            compare(request.getTaxonomy(),revisions.lockTaxonomy(true),true);
            if(request.getParent()!=null)revisions.lockVersions(Resource.CATEGORY,List.of(request.getParent()),true);
            Category parent=request.getParent()==null?null:fresh(request.getParent());String name=name(request.getName());
            if(queries.categoryNameExists(name,request.getParent(),null))throw conflict();
            Map<String,BindingScope> before=queries.bindings();revisions.claimTaxonomy(request.getTaxonomy());
            Category category=BeanUtils.instantiateClass(Category.class);category.setName(name);category.setParentCategory(parent);category=categories.saveAndFlush(category);
            List<Binding> changed=validateChangedBindings(before,queries.bindings());
            return new CategoryMutation(view(category),Long.toString(request.getTaxonomy()+1),changed);
        });
    }
    @Override public CategoryMutation update(UUID id,CategoryPatch request){
        CatalogAggregateMutation.requireCompatibleTransaction();return transactions.execute(status->{
            compare(request.getTaxonomy(),revisions.lockTaxonomy(true),true);
            Category observed=fresh(id);
            List<UUID> locks=new ArrayList<>(List.of(id));UUID referenced=request.getParent().isSupplied()?request.getParent().getValue():parentId(observed);if(referenced!=null)locks.add(referenced);
            compare(request.getVersion(),revisions.lockVersions(Resource.CATEGORY,locks,true).get(id),false);
            Category category=fresh(id);UUID oldParent=parentId(category);UUID parent=request.getParent().isSupplied()?request.getParent().getValue():oldParent;
            String name=request.getName().isSupplied()?name(request.getName().getValue()):category.getName();
            if(request.getParent().isSupplied() && parent!=null && queries.descendants(id).contains(parent))throw new AdminCatalogException(422,"CATALOG_VALIDATION_FAILED","A Category cannot be its own descendant");
            if(queries.categoryNameExists(name,parent,id))throw conflict();
            if(name.equals(category.getName()) && Objects.equals(parent,oldParent))return new CategoryMutation(view(category),Long.toString(request.getTaxonomy()),List.of());
            Category proposedParent=parent==null?null:fresh(parent);Map<String,BindingScope> before=queries.bindings();
            revisions.claimTaxonomy(request.getTaxonomy());revisions.claim(Resource.CATEGORY,id,request.getVersion());revisions.refresh(category);
            category.setName(name);category.setParentCategory(proposedParent);categories.flush();
            List<Binding> changed=validateChangedBindings(before,queries.bindings());
            return new CategoryMutation(view(category),Long.toString(request.getTaxonomy()+1),changed);
        });
    }
    @Override public void delete(UUID id,long version,long taxonomy){
        CatalogAggregateMutation.requireCompatibleTransaction();transactions.executeWithoutResult(status->{
            compare(taxonomy,revisions.lockTaxonomy(true),true);compare(version,revisions.lockVersions(Resource.CATEGORY,List.of(id),true).get(id),false);
            Category category=fresh(id);if(queries.categoryReferenced(id))throw new AdminCatalogException(409,"CATALOG_DEPENDENCY_CONFLICT","Category still has Products or child Categories");
            Map<String,BindingScope> before=queries.bindings();revisions.claimTaxonomy(taxonomy);revisions.claim(Resource.CATEGORY,id,version);revisions.refresh(category);
            categories.delete(category);categories.flush();validateChangedBindings(before,queries.bindings());
        });
    }
    private List<Binding> validateChangedBindings(Map<String,BindingScope> before,Map<String,BindingScope> after){
        Set<String> families=new HashSet<>(before.keySet());families.addAll(after.keySet());Set<UUID> possible=new HashSet<>();
        before.values().forEach(b->possible.addAll(b.categories()));after.values().forEach(b->possible.addAll(b.categories()));
        Set<UUID> affected=new HashSet<>();for(UUID category:possible)if(!applicable(before,category).equals(applicable(after,category)))affected.add(category);
        List<UUID> products=queries.publishedInCategories(Set.copyOf(affected)).stream().sorted(CatalogMutationRepository.UUID_ORDER).toList();
        if(!products.isEmpty()){revisions.lockVersions(Resource.PRODUCT,products,true);for(UUID product:products)aggregates.requirePublication(revisions.freshProduct(product));}
        List<Binding> changes=new ArrayList<>();
        for(String family:families.stream().sorted().toList()){
            BindingScope previous=before.get(family),current=after.get(family);if(Objects.equals(previous,current))continue;
            boolean active=current!=null && current.matches()==1;
            changes.add(new Binding(family,active?"ACTIVE":"DISABLED",active?"CHANGED_ANCESTRY":current==null || current.matches()==0?"MISSING":"AMBIGUOUS"));
        }
        return List.copyOf(changes);
    }
    private static Set<String> applicable(Map<String,BindingScope> snapshot,UUID category){Set<String> result=new HashSet<>();snapshot.forEach((family,scope)->{if(scope.categories().contains(category))result.add(family);});return result;}
    private String name(String raw){if(raw==null || raw.isBlank())throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Category name is required");String name=queries.normalizeName(raw);if(name==null || name.isEmpty())throw new AdminCatalogException(422,"CATALOG_VALIDATION_FAILED","Category name is empty after normalization");return name;}
    private Category fresh(UUID id){Category category=categories.findById(id).orElseThrow(()->new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Category not found"));revisions.refresh(category);return category;}
    private com.example.demo.catalog.dto.response.AdminCatalogResponses.Category view(Category category){return converter.category(category,parentId(category));}
    private static UUID parentId(Category category){return category.getParentCategory()==null?null:category.getParentCategory().getId();}
    private static void compare(long expected,long current,boolean taxonomy){if(expected<0)throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid revision");if(expected!=current)throw new AdminCatalogException(409,"CATALOG_STALE_VERSION","Taxonomy changed; reload before retrying",Map.of(taxonomy?"expectedTaxonomyVersion":"expectedVersion",Long.toString(expected),taxonomy?"currentTaxonomyVersion":"currentVersion",Long.toString(current)));}
    private static AdminCatalogException conflict(){return new AdminCatalogException(409,"CATALOG_NAME_CONFLICT","Category name conflicts with a sibling");}
}
