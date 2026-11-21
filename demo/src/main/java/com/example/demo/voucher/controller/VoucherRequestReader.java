package com.example.demo.voucher.controller;
import jakarta.servlet.http.HttpServletRequest;
import com.example.demo.voucher.exception.InvalidVoucherRequestException;
import org.springframework.stereotype.Component;
@Component
public class VoucherRequestReader {
    public int[] readPage(HttpServletRequest request) {
        noBody(request);
        for(var entry:request.getParameterMap().entrySet()) {
            if(!(entry.getKey().equals("page") || entry.getKey().equals("size")) || entry.getValue().length!=1) throw new InvalidVoucherRequestException();
        }
        int page=number(request.getParameter("page"),0),size=number(request.getParameter("size"),20);
        if(size<1 || size>100) throw new InvalidVoucherRequestException();
        return new int[]{page,size};
    }
    public Long readDetail(HttpServletRequest request,String id) {
        noBody(request);
        if(!request.getParameterMap().isEmpty() || (request.getQueryString()!=null && !request.getQueryString().isEmpty()) || !id.matches("[1-9][0-9]*")) throw new InvalidVoucherRequestException();
        try {return Long.valueOf(id);} catch(NumberFormatException bad) {throw new InvalidVoucherRequestException();}
    }
    private int number(String value,int fallback) {
        if(value==null) return fallback;
        if(!value.matches("0|[1-9][0-9]*")) throw new InvalidVoucherRequestException();
        try {return Integer.parseInt(value);} catch(NumberFormatException bad) {throw new InvalidVoucherRequestException();}
    }
    private void noBody(HttpServletRequest request) {
        try {if(request.getInputStream().readAllBytes().length!=0) throw new InvalidVoucherRequestException();}
        catch(java.io.IOException bad) {throw new InvalidVoucherRequestException();}
    }
}
