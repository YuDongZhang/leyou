package com.leyou.goods.client;

import com.leyou.item.api.GoodsApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("item-service")
public interface GoodsClient extends GoodsApi {
    /**
     * 根据spuId查询spuDetail
     * @param spuId
     * @return
     *
     * 本来是要远程调用的 ,但是实际可能不知道接口参数 ,这个就需要开发人员提供一个接口 , 见 item-interface
     */
    //上面是没有继承的
//    @GetMapping("spu/detail/{spuId}")//这种写法要解析
//    public ResponseEntity<SpuDetail> querySpuDetailBySpuId(@PathVariable("spuId")Long spuId){
//    @GetMapping("spu/detail/{spuId}")//这种直接返回
//    public SpuDetail querySpuDetailBySpuId(@PathVariable("spuId")Long spuId);


}
