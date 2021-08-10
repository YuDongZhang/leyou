package cn.itcast.elasticsearch;

import cn.itcast.elasticsearch.pojo.Item;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ItemRepository extends ElasticsearchCrudRepository<Item,Long> {//Long为对象主键
}
