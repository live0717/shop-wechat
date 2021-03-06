package com.wy.shop.service.impl;

import com.wy.shop.entity.*;
import com.wy.shop.mapper.AddressMapper;
import com.wy.shop.mapper.CartMapper;
import com.wy.shop.mapper.FreightMapper;
import com.wy.shop.request.AddCartReq;
import com.wy.shop.service.CartService;
import com.wy.shop.service.GoodsService;
import com.wy.shop.service.GoodsSpecificationService;
import com.wy.shop.service.ProductService;
import com.wy.shop.vo.CartCheckoutVo;
import com.wy.shop.vo.CartIndexVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    public static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    @Resource
    private CartMapper cartMapper;
    @Autowired
    private ProductService productService;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private GoodsSpecificationService goodsSpecificationService;
    @Resource
    private AddressMapper addressMapper;
    @Autowired
    private FreightMapper freightMapper;

    @Override
    public Integer getGoodsCount(Integer uid) {
        if (cartMapper.getGoodsCount(uid) == null) {
            return 0;
        }
        return cartMapper.getGoodsCount(uid);
    }

    /**
     * 获取购物车首页信息
     *
     * @param indexCartSearch
     * @return
     */
    @Override
    public CartIndexVo getIndex(IndexCartSearch indexCartSearch) {

        CartIndexVo cartIndexVo = new CartIndexVo();

        //根据用户id直接查询购物车的商品信息，满足的条件为is_fast=0,is_delete=0
        List<Cart> cartList = cartMapper.getCartList(indexCartSearch);

        int goodsCount = 0;
        BigDecimal amount = new BigDecimal("0");
        for (Cart cart : cartList) {
            BigDecimal goods_amount = cart.getRetail_price();
            Integer number = cart.getNumber();
            BigDecimal amountCount = goods_amount.multiply(new BigDecimal(number.toString()));
            cart.setWeight_count(amountCount);
            goodsCount = goodsCount + number;
            amount = amount.add(amountCount);
        }

        IndexCartTotal indexCartTotal = new IndexCartTotal();
        indexCartTotal.setUser_id(indexCartSearch.getUid());
        indexCartTotal.setGoodsCount(goodsCount);
        indexCartTotal.setGoodsAmount(String.valueOf(amount));
        indexCartTotal.setCheckedGoodsAmount(String.valueOf(amount));
        indexCartTotal.setCheckedGoodsCount(goodsCount);
        indexCartTotal.setNumberChange(0);

        cartIndexVo.setCartList(cartList);
        cartIndexVo.setCartTotal(indexCartTotal);
        return cartIndexVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartIndexVo addCart(AddCartReq addCartReq, Integer userId) {
        int productId = addCartReq.getProductId();
        String goodsId = addCartReq.getGoodsId();
        int number = addCartReq.getNumber();

        Product product = productService.getProductById(productId);
        Goods goods = goodsService.getGoodsById(Integer.valueOf(goodsId));

        //判断购物车是否已经有该商品，有的话则更新购物车信息
        Integer cid = cartMapper.getCartByUidAndPid(userId, productId);
        if (cid != null) {
            //更新购物车商品的数量
            cartMapper.updateProductNumber(cid, number);
        } else {
            //添加购物车商品信息
            Cart cart = new Cart();
            cart.setAdd_price(product.getRetail_price());
            cart.setAdd_time(System.currentTimeMillis());
            cart.setChecked(1);
            cart.setFreight_template_id(goods.getFreight_template_id());
            cart.setGoods_aka(goods.getGoods_brief());
            cart.setGoods_id(goods.getId());
            cart.setGoods_name(goods.getName());
            cart.setGoods_sn(product.getGoods_sn());
            cart.setGoods_specifition_ids(product.getGoods_specification_ids());
            String value = goodsSpecificationService.getSpecificationValueById(Integer.valueOf(product.getGoods_specification_ids()));
            cart.setGoods_specifition_name_value(value);
            cart.setGoods_weight(product.getGoods_weight());
            cart.setIs_delete(product.getIs_delete());
            cart.setIs_fast(addCartReq.getAddType());
            cart.setIs_on_sale(goods.getIs_on_sale());
            cart.setList_pic_url(goods.getList_pic_url());
            cart.setNumber(number);
            cart.setProduct_id(product.getId());
            cart.setRetail_price(product.getRetail_price());
            cart.setUser_id(userId);
            cartMapper.addCart(cart);
        }

        //展示购物车信息
        IndexCartSearch indexCartSearch = new IndexCartSearch();
        indexCartSearch.setUid(userId);
        indexCartSearch.setIsFast(addCartReq.getAddType());
        CartIndexVo index = getIndex(indexCartSearch);
        return index;
    }

    @Override
    public CartIndexVo updateCart(Integer id, Integer number, Integer userId) {
        cartMapper.updateCart(id, number);
        IndexCartSearch indexCartSearch = new IndexCartSearch();
        indexCartSearch.setUid(userId);
        indexCartSearch.setIsFast(0);
        CartIndexVo index = getIndex(indexCartSearch);
        return index;
    }

    @Override
    public CartIndexVo updateIsCheck(Integer ischeck, Integer productid, Integer userId) {
        cartMapper.updateIsCheck(ischeck, productid, userId);
        IndexCartSearch indexCartSearch = new IndexCartSearch();
        indexCartSearch.setUid(userId);
        indexCartSearch.setIsFast(0);
        CartIndexVo index = getIndex(indexCartSearch);
        return index;
    }


    @Override
    public CartCheckoutVo checkOut(Integer addType, Integer addressId, Integer uid) {
        //查询购买后的信息
        IndexCartSearch indexCartSearch = new IndexCartSearch();
        indexCartSearch.setUid(uid);
        indexCartSearch.setIsFast(addType);
        CartIndexVo index = getIndex(indexCartSearch);

        List<Cart> cartList = index.getCartList();
        List<Integer> templateIdList = cartList.stream().map(cart ->
                cart.getFreight_template_id()).collect(Collectors.toList());

        CartCheckoutVo cartCheckoutVo = new CartCheckoutVo();
        List<FreightPrice> freightPriceList = null;
        //根据地址的ID查询地址信息
        if (addressId == 0) {
            cartCheckoutVo.setCheckedAddress(null);
            cartCheckoutVo.setFreightPrice(new BigDecimal("0"));
        } else {
            //Address address = addressMapper.findAddressById(addressId);
            ExtAddress extAddress = addressMapper.findExtAddressById(addressId);
            Integer province_id = extAddress.getProvince_id();
            //TODO 如果是全国包邮该怎么处理

            freightPriceList = freightMapper.getFreightPrice(province_id, templateIdList);
            log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>freightPriceList:{}" + freightPriceList);
            cartCheckoutVo.setCheckedAddress(extAddress);
            cartCheckoutVo.setFreightPrice(freightPriceList.get(0).getStartFee());
        }

        cartCheckoutVo.setGoodsCount(index.getCartTotal().getGoodsCount());
        BigDecimal totalPrice = new BigDecimal(index.getCartTotal().getCheckedGoodsAmount());
        totalPrice = totalPrice.add(freightPriceList.get(0).getStartFee());
        cartCheckoutVo.setOrderTotalPrice(totalPrice.toString());
        cartCheckoutVo.setActualPrice(totalPrice.toString());
        cartCheckoutVo.setGoodsTotalPrice(index.getCartTotal().getCheckedGoodsAmount());
        cartCheckoutVo.setCheckedGoodsList(index.getCartList());
        cartCheckoutVo.setNumberChange(0);
        cartCheckoutVo.setOutStock(0);
        return cartCheckoutVo;
    }

    @Override
    public void updateFastCheck(Integer userId, Integer goodsId) {
        cartMapper.updateFastCheck(userId, goodsId);
    }

}
