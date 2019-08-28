package com.aura.service;

import com.aura.basic.BasicServiceSupportImpl;
import com.aura.dao.ShopInfoDao;
import com.aura.model.*;
import com.aura.model.result.CityConsume;
import com.aura.model.result.MostViewShop;
import com.aura.model.result.PopuShopTrade;
import com.aura.model.result.ShopTradeView;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("shopInfoService")
public class ShopInfoService extends BasicServiceSupportImpl{

    @Resource
    private ShopInfoDao shopInfoDao;

    //SparkStreaming中用到的shop与城市对应表
    public Map<String,String> getShopInfoList() {
        List<ShopInfo> list = (List<ShopInfo>)shopInfoDao.selectList("common.shopInfo.getStreamShopCityList", null);
        Map<String,String> shopCityMap = new HashMap<>();
        list.stream().forEach(info -> shopCityMap.put(info.getShopId().toString(),info.getCityName()));
        return shopCityMap;
    }

    //HBase中用到的根据shop_id查询shop_info信息
    @Cacheable(value = {"ShopInfo"},key = "shopId") //没生效？？？
    public ShopInfo getShopInfoById(Integer shopId) {
        ShopInfo info = (ShopInfo) shopInfoDao.selectObject("common.shopInfo.getShopInfoById",shopId);
        return info;
    }

    public List<TradeAcount> getTradeAcountList() {
        List<TradeAcount> list = (List<TradeAcount>)shopInfoDao.selectList("common.shopInfo.getTradeAcount", null);
        return list;
    }

    public List<PopulShop> getPopulShopList(String cate) {
        List<PopulShop> list = (List<PopulShop>)shopInfoDao.selectList("common.shopInfo.getPopulShopList", cate);
        return list;
    }

    public List<CityTrade> getCityTradeList() {
        List<CityTrade> list = (List<CityTrade>)shopInfoDao.selectList("common.shopInfo.getCityTradeList", null);
        return list;
    }

    public List<ProvinceTrade> getProvinceTradeList() {
        List<ProvinceTrade> list = (List<ProvinceTrade>)shopInfoDao.selectList("common.shopInfo.getProvinceTrade", null);
        return list;
    }


    public List<MerchantTrade> getMerchantTradeList() {
        List<MerchantTrade> list = (List<MerchantTrade>)shopInfoDao.selectList("common.shopInfo.getMerchantTrade", null);
        return list;
    }

    public List<ShopTradeView> getShopTradeView() {
        List<ShopTradeView> list = (List<ShopTradeView>) shopInfoDao.selectList("common.shopInfo.getShopTradeView",null);
        return list;
    }

    public List<CityConsume> getCityConsume() {
        List<CityConsume> list = (List<CityConsume>) shopInfoDao.selectList("common.shopInfo.getCityConsume",null);
        return list;
    }

    public List<PopuShopTrade> getPopuShopTrade() {
        List<PopuShopTrade> list = (List<PopuShopTrade>) shopInfoDao.selectList("common.shopInfo.getPopuShopTrade",null);
        return list;
    }

    public List<MostViewShop> getMostViewShop() {
        List<MostViewShop> list = (List<MostViewShop>) shopInfoDao.selectList("common.shopInfo.getMostViewShop",null);
        return  list;
    }
}
