package com.example.bishe.service;
import com.example.bishe.common.BusinessException;
import java.util.Set;
public final class PageSize {
    private PageSize(){}
    public static int checked(int size){if(!Set.of(5,10,20,50).contains(size))throw new BusinessException("每页条数只能为5、10、20或50");return size;}
    public static int offset(int page,int size){return (Math.max(1,Math.min(100000,page))-1)*size;}
}
