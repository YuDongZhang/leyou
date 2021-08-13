package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.item.pojo.*;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import com.leyou.search.repository.GoodsRepository;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
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

    @Autowired
    private GoodsRepository goodsRepository;

    public SearchResult search(SearchRequest request) {
        System.out.println("search 此方法执行了");
        String key = request.getKey();
        // 判断是否有搜索条件，如果没有，直接返回null。不允许搜索全部商品
        if (StringUtils.isBlank(key)) {
            return null;
        }

        // 构建查询条件
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

        // 1、对key进行全文检索查询
        queryBuilder.withQuery(QueryBuilders.matchQuery("all", key).operator(Operator.AND));

        // 2、通过sourceFilter设置返回的结果字段,我们只需要id、skus、subTitle .添加结果集过滤
        queryBuilder.withSourceFilter(new FetchSourceFilter(
                new String[]{"id","skus","subTitle"}, null));

        //添加分类和品牌的聚合
        String categoryAggName = "categories";
        String brandAggName = "brands";
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));

        // 3、分页 ,分页页码从0开始
        // 准备分页参数
        int page = request.getPage();
        int size = request.getSize();
        queryBuilder.withPageable(PageRequest.of(page - 1, size));

        // 4、查询，获取结果 ,这种方式包含聚合结果集
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepository.search(queryBuilder.build());

        //获取聚合结果集并解析
        // 解析聚合结果集  分类和品牌
        List<Map<String, Object>> categories = getCategoryAggResult(goodsPage.getAggregation(categoryAggName));
        List<Brand> brands = getBrandAggResult(goodsPage.getAggregation(brandAggName));


        // 封装结果并返回
        return new SearchResult(goodsPage.getTotalElements(), goodsPage.getTotalPages(), goodsPage.getContent(),categories,brands);
    }

    /**
     * 解析品牌的聚合结果集
     * @param aggregation
     * @return
     */
    private List<Brand> getBrandAggResult(Aggregation aggregation) {
        /*
        "aggregations": {
    "brands": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 0,
      "buckets": [
        {
          "key": 76,
          "doc_count": 182
        }
      ]
    }
  }
         */
        // 处理聚合结果集
        LongTerms terms = (LongTerms)aggregation;
        // 获取所有的品牌id桶 ,对应的是 kiban 查询到的结果集
        List<LongTerms.Bucket> buckets = terms.getBuckets();
        // 定义一个品牌集合，搜集所有的品牌对象
        List<Brand> brands = new ArrayList<>();
        // 解析所有的id桶，查询品牌
        buckets.forEach(bucket -> {
            Brand brand = this.brandClient.queryBrandById(bucket.getKeyAsNumber().longValue());
            brands.add(brand);
        });
        return brands;
    }

    /**
     * 解析分类聚合结果集
     * @param aggregation
     * @return
     */
    private List<Map<String, Object>> getCategoryAggResult(Aggregation aggregation) {
        /*
        "aggregations": {
    "categories": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 0,
      "buckets": [
        {
          "key": 76,
          "doc_count": 182
        }
      ]
    }
  }
         */
        LongTerms terms =  (LongTerms) aggregation;
        return terms.getBuckets().stream().map(bucket -> {
            Map<String,Object> map = new HashMap<>();
            Long id = bucket.getKeyAsNumber().longValue();//获取分类id,也就是上面的key
            //根据分类id查询分类名称
            List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(id));
            map.put("id",id);
            map.put("name",names.get(0));
            return map;
        }).collect(Collectors.toList());
    }
}
