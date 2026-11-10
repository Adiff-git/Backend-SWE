package com.example.demo.catalog.validator;

import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.ProductSearchSort;
import java.util.HashSet;

/** Cross-field rules also apply to internal validated-request callers,not only HTTP parsing. */
public final class ProductSearchValidator {
    private ProductSearchValidator(){}
    public static void validate(ProductSearchRequest request){
        if(request.getMinPrice()!=null && request.getMaxPrice()!=null && request.getMinPrice().compareTo(request.getMaxPrice())>0){throw invalid();}
        if(request.getSort()==ProductSearchSort.RELEVANCE && new ProductSearchNormalizer().normalizeQuery(request.getQ()).getTokens().isEmpty()){throw invalid();}
        if(request.getSpecificationGroups()!=null){
            if(request.getCategoryId()==null){throw invalid();}
            var keys=new HashSet<String>();
            for(var group:request.getSpecificationGroups()){
                if(!keys.add(group.getKey())){throw invalid();}
                for(var operand:group.getAlternatives()){
                    if(operand.getMin()!=null&&operand.getMax()!=null&&operand.getMin().compareTo(operand.getMax())>0){throw invalid();}
                }
            }
        }
    }
    private static InvalidCatalogRequestException invalid(){return new InvalidCatalogRequestException("Invalid search parameter combination");}
}
