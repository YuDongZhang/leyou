package cn.itcast.elasticsearch;

import cn.itcast.elasticsearch.pojo.Item;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

import java.util.List;

public interface ItemRepository extends ElasticsearchCrudRepository<Item,Long> {//Long为对象主键
    /**
     * 根据价格区间查询
     * @param price1
     * @param price2
     * @return
     */
    List<Item> findByPriceBetween(double price1, double price2);
}
