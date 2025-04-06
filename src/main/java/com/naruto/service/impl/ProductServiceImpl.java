package com.naruto.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.naruto.mapper.ProductMapper;
import com.naruto.model.entity.Product;
import com.naruto.service.ProductService;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

}
