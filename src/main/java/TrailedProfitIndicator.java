import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

public class TrailedProfitIndicator extends Indicator {
    private double trailLimit;

    public TrailedProfitIndicator(MongoCollection<Document> history, HistoryItem historyItem, double trailLimit) {
        super();
        this.trailLimit = trailLimit;
        this.historyItem = historyItem;
        this.historyData = history;
    }

    @Override
    public double getValue(String field, long timestamp) {
        return 0;
    }

    @Override
    public String getClassId() {
        return "TrailedProfit-"+String.valueOf(Math.round(trailLimit*1000))+"-"+historyItem.getClassId();
    }

    public String getId() {
        return "TrailedProfit-"+String.valueOf(Math.round(trailLimit*1000))+"-"+historyItem.getClassId();
    }

    @Override
    public void update() {
        System.out.println("Updating values for TrailedProfit indicator...");
        //Determine start time for updating
        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        ProfitItem profitItem = new ProfitItem(this.getClassId());
        Document lastValue = historyData.find(eq("class",profitItem.getClassId()))
                .sort(Sorts.descending("timestamp")).first();
        if (lastValue != null) {
            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
        } else {
            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
        }
        ArrayList<HistoryItem> history = new ArrayList<>();
        MongoCursor<Document> cursor = historyData
                .find(and(eq("class",historyItem.getClassId()),gt("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.ascending("timestamp")).iterator();
        while (cursor.hasNext()) {
            HistoryItem hItem = HistoryItem.createFromDocument(cursor.next());
            history.add(hItem);
        }
        for (int currentItem = 0; currentItem<history.size(); currentItem++) {
            HistoryItem hItem = history.get(currentItem);
            profitItem.timestamp = hItem.timestamp;
            double buyPrice = hItem.data.close();
            double sellPrice = hItem.data.close();
            double sellLimit = sellPrice*(1+trailLimit);
            double buyLimit = buyPrice*(1-trailLimit);
            boolean buyOpen = true;
            boolean sellOpen = true;
            for (int index = currentItem; index < history.size(); index++) {
                HistoryItem nextItem = history.get(index);
                if (sellOpen && nextItem.data.close()>sellLimit) {
                    sellOpen = false;
                    profitItem.sellProfit = sellPrice - nextItem.data.close();
                    profitItem.sellROI = profitItem.sellProfit * (365*24*3600*1000) / (nextItem.timestamp - profitItem.timestamp) / sellPrice;
                }
                if (buyOpen && nextItem.data.close()<buyLimit) {
                    buyOpen = false;
                    profitItem.buyProfit = nextItem.data.close() - buyPrice;
                    profitItem.buyROI = profitItem.buyProfit * (365*24*3600*1000) / (nextItem.timestamp - profitItem.timestamp) / buyPrice;
                }
                double newSellLimit = nextItem.data.close()*(1+trailLimit);
                if (sellOpen && newSellLimit<sellLimit) {
                    sellLimit = newSellLimit;
                }
                double newBuyLimit = nextItem.data.close()*(1-trailLimit);
                if (buyOpen && newBuyLimit>buyLimit) {
                    buyLimit = newBuyLimit;
                }
                if (!sellOpen && !buyOpen) break;
            }
            historyData.insertOne(profitItem.getDocument());
            startOfPeriod.setTimeInMillis(hItem.timestamp);
            System.out.printf("TrailedProfit value updated for date %1$tD %1$tT with buy profit/ROI %2$.3f/%3$.0f%% sell profit/ROI %4$.3f/%5$.0f%% %n",
                    startOfPeriod,profitItem.buyProfit,profitItem.buyROI*100,profitItem.sellProfit,profitItem.sellROI*100);
        }
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getId());
        doc.append("barSize", historyItem.barSize);
        doc.append("dataType", historyItem.dataType);
        doc.append("exchange", historyItem.contract.exchange());
        doc.append("secType", historyItem.contract.secType());
        doc.append("currency", historyItem.contract.currency());
        doc.append("symbol", historyItem.contract.symbol());
        doc.append("trailLimit", trailLimit);
        doc.append("indicatorType", "TrailedProfit");
        doc.append("class", getClassId());
        return doc;
    }

    public static TrailedProfitIndicator createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        double trailLimit = doc.getDouble("trailLimit");
        return new TrailedProfitIndicator(history,item,trailLimit);
    }

}
