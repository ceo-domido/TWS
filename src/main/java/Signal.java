import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import static com.mongodb.client.model.Filters.*;

public abstract class Signal {
    protected HistoryItem historyItem;
    protected MongoCollection<Document> historyData;

    public double getValue(String field) {
        return getValue(field,0);
    }

    public double getValue(long timestamp) {
        return getValue("close",timestamp);
    }

    public double getValue() {
        return getValue("close",0);
    }

    public abstract double getValue(String field, long timestamp);
    public abstract String getClassId();
    public abstract void update();
    public abstract Document getDocument();
}
