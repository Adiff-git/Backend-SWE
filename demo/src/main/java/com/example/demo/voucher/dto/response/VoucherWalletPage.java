package com.example.demo.voucher.dto.response;
import java.util.List;
import lombok.Value;
@Value
public class VoucherWalletPage {
    List<VoucherWalletEntry> items; int page; int size; long totalElements; long totalPages; boolean first; boolean last;
    public VoucherWalletPage(List<VoucherWalletEntry> items,int page,int size,long count) {
        this.items=List.copyOf(items);this.page=page;this.size=size;totalElements=count;
        totalPages=count/size+(count%size==0 ? 0 : 1);first=page==0;last=(long)page+1>=totalPages;
    }
}
