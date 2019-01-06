import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.Document;

import java.util.Map;

public interface MongoWrapper<T> {

    Document getDocument();
}
