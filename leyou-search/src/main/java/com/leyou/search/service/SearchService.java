package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.item.pojo.*;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;

public class SearchService {

    @Autowired
    private BrandClient brandClient;

    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private SpecificationClient specificationClient;

    @Autowired
    private GoodsClient goodsClient;

    //json工具
    private static final ObjectMapper MAPPER = new ObjectMapper();

    //第一步
    public Goods buildGoods(Spu spu) throws IOException {


        // 根据分类id,查询分类名称
        List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));

        // 根据品牌id 查询品牌
        Brand brand = this.brandClient.queryBrandById(spu.getBrandId());

        // 查询spu下的所有sku
        List<Sku> skus = this.goodsClient.querySkusBySpuId(spu.getId());
        //初始化一个价格集合,收集所有的sku价格
        List<Long> prices = new ArrayList<>();
        //收集sku必要字段信息
        List<Map<String, Object>> skuMapList = new ArrayList<>();
        // 遍历skus，获取价格集合
        skus.forEach(sku -> {
            prices.add(sku.getPrice());

            Map<String, Object> skuMap = new HashMap<>();
            skuMap.put("id", sku.getId());
            skuMap.put("title", sku.getTitle());
            skuMap.put("price", sku.getPrice());
            //获取sku中的图片,数据库的图片可能是多张,多张以","分隔,用逗号切割返回数组,获取第一张图片
            skuMap.put("image", StringUtils.isNotBlank(sku.getImages()) ? StringUtils.split(sku.getImages(), ",")[0] : "");
            skuMapList.add(skuMap);
        });

        // 根据spu中的cid3查询出所有的搜索规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, spu.getCid3(), null, true);

        // 查询spuDetail。获取规格参数值
        SpuDetail spuDetail = this.goodsClient.querySpuDetailBySpuId(spu.getId());

        // 获取通用的规格参数,把通用规格参数的值,进行反序列化
        Map<Long, Object> genericSpecMap = MAPPER.readValue(spuDetail.getGenericSpec(), new TypeReference<Map<Long, Object>>() {
        });

        // 获取特殊的规格参数 , 反序列化
        Map<Long, List<Object>> specialSpecMap = MAPPER.readValue(spuDetail.getSpecialSpec(), new TypeReference<Map<Long, List<Object>>>() {
        });

        // 定义map接收{规格参数名，规格参数值}
        Map<String, Object> paramMap = new HashMap<>();
        params.forEach(param -> {
            // 判断是否通用规格参数
            if (param.getGeneric()) {
                // 获取通用规格参数值
                String value = genericSpecMap.get(param.getId()).toString();
                // 判断是否是数值类型
                if (param.getNumeric()) {
                    // 如果是数值的话，判断该数值落在那个区间
                    value = chooseSegment(value, param);
                }
                // 把参数名和值放入结果集中
                paramMap.put(param.getName(), value);
            } else {
                paramMap.put(param.getName(), specialSpecMap.get(param.getId().toString()));//这里和老师保持一致
            }
        });

        Goods goods = new Goods();
        // 设置参数
        goods.setId(spu.getId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setBrandId(spu.getBrandId());
        goods.setCreateTime(spu.getCreateTime());
        goods.setSubTitle(spu.getSubTitle());
        //拼接all字段,需要分类名称和品牌名称
        goods.setAll(spu.getTitle() + " " + StringUtils.join(names, " ") + " " + brand.getName());
        //获取spu下所有sku价格
        goods.setPrice(prices);
        //获取spu下所有sku,并转化为json字符串
        goods.setSkus(MAPPER.writeValueAsString(skuMapList));
        //获取所有查询的规格参数{name:value}
        goods.setSpecs(paramMap);
        return goods;
    }

    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if (segs.length == 2) {
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if (val >= begin && val < end) {
                if (segs.length == 1) {
                    result = segs[0] + p.getUnit() + "以上";
                } else if (begin == 0) {
                    result = segs[1] + p.getUnit() + "以下";
                } else {
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }
}
