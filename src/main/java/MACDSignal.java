import com.ib.client.Contract;
import com.mongodb.Function;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.bson.Document;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.mongodb.client.model.Filters.*;

public class MACDSignal extends Indicator {
    private int window = 10;
    private MACDIndicator macdIndicator;

    public MACDSignal(MongoCollection<Document> history, HistoryItem historyItem, int shortPeriod, int longPeriod, int averagePeriod) {
        this.historyData = history;
        this.historyItem = historyItem;
        this.macdIndicator = new MACDIndicator(history,historyItem,shortPeriod,longPeriod,averagePeriod);
    }

    @Override
    public double getValue(String field, long timestamp) {
        return 0;
    }

    @Override
    public String getClassId() {
        return "MACDSignal-"+String.valueOf(window)+"-"+historyItem.getClassId();
    }

    @Override
    public void update() {
        macdIndicator.update();
        System.out.println("Updating values for MACD signal...");
        // start is now
        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        // initialize current value
        SignalItem signalItem = new SignalItem(this.getClassId());
        // find last EMA record
        Document lastValue = historyData.find(eq("class",signalItem.getClassId()))
                .sort(Sorts.descending("timestamp")).first();
        // if last record exist then start from it else start from some months later
        if (lastValue != null) {
            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
            signalItem = SignalItem.createFromDocument(lastValue);
        } else {
            // compute start value
            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
        }
        CircularFifoQueue<MACDItem> macdItems = new CircularFifoQueue<>();
        CircularFifoQueue<EMAItem> shortEmaItems = new CircularFifoQueue<>();
        CircularFifoQueue<EMAItem> longEmaItems = new CircularFifoQueue<>();
        MongoCursor<Document> cursor = historyData
                .find(and(eq("class",macdIndicator.getClassId()),gt("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.ascending("timestamp")).iterator();
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            MACDItem newItem = MACDItem.createFromDocument(doc);
            macdItems.add(newItem);
            Document shortEMADoc = historyData
                    .find(and(eq("class",macdIndicator.shortEMA.getClassId()),eq("timestamp",newItem.timestamp)))
                    .first();
            Document longEMADoc = historyData
                    .find(and(eq("class",macdIndicator.longEMA.getClassId()),eq("timestamp",newItem.timestamp)))
                    .first();
            if (shortEMADoc != null && longEMADoc != null) {
                EMAItem shortEMA = EMAItem.createFromDocument(shortEMADoc);
                EMAItem longEMA = EMAItem.createFromDocument(longEMADoc);
                shortEmaItems.add(shortEMA);
                longEmaItems.add(longEMA);

                historyData.insertOne(signalItem.getDocument());
                System.out.println("EMA value updated for date ");
            }
        }
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getClassId());
        doc.append("barSize", historyItem.barSize);
        doc.append("dataType", historyItem.dataType);
        doc.append("exchange", historyItem.contract.exchange());
        doc.append("secType", historyItem.contract.secType());
        doc.append("currency", historyItem.contract.currency());
        doc.append("symbol", historyItem.contract.symbol());
        doc.append("window", window);
        doc.append("macdIndicator", macdIndicator.getDocument());
        doc.append("indicatorType", "MACDSignal");
        doc.append("class",getClassId());
        return doc;
    }

    public static MACDSignal createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int window = doc.getInteger("window");
        MACDIndicator macd = MACDIndicator.createFromDocument(doc.get("macdIndicator", Document.class), history);
        MACDSignal result = new MACDSignal(history, item, 0,0,0);
        result.macdIndicator = macd;
        return result;
    }

    public double testProfit(String profitClass, int shortPeriod, int longPeriod, int averagePeriod) {
        MongoCursor<Document> cursor = historyData
                .find(eq("class",historyItem.getClassId()))
                .sort(Sorts.ascending("timestamp")).iterator();
        Document firstDoc = cursor.next();
        if (firstDoc == null) {
            return 0;
        }
        HistoryItem firstItem = HistoryItem.createFromDocument(firstDoc);
        double sA = 2.0/(1+shortPeriod);
        double lA = 2.0/(1+longPeriod);
        double aA = 2.0/(1+averagePeriod);
        double sEMA = firstItem.data.close();
        double lEMA = firstItem.data.close();
        double MACD = 0.0;
        double prevMACD;
        double result = 0.0;
        while (cursor.hasNext()) {
            HistoryItem nextItem = HistoryItem.createFromDocument(cursor.next());
            sEMA = nextItem.data.close()*sA + sEMA*(1-sA);
            lEMA = nextItem.data.close()*lA + lEMA*(1-lA);
            prevMACD = MACD;
            MACD = (sEMA-lEMA)*aA + MACD*(1-aA);
            if (prevMACD<0 && MACD>0) {
                Document profitDoc = historyData.find(and(eq("class",profitClass),eq("timestamp",nextItem.timestamp))).first();
                if (profitDoc != null) {
                    ProfitItem item = ProfitItem.createFromDocument(profitDoc);
                    result += item.buyProfit;
                }
            }
            if (prevMACD>0 && MACD<0) {
                Document profitDoc = historyData.find(and(eq("class",profitClass),eq("timestamp",nextItem.timestamp))).first();
                if (profitDoc != null) {
                    ProfitItem item = ProfitItem.createFromDocument(profitDoc);
                    result += item.sellProfit;
                }
            }
        }
        return result;
    }

    public double testProfit(Double[] price, Double[] buyProfit, Double[] sellProfit, int shortPeriod, int longPeriod, int averagePeriod) {
        double sA = 2.0/(1+shortPeriod);
        double lA = 2.0/(1+longPeriod);
        double aA = 2.0/(1+averagePeriod);
        double sEMA = price[0];
        double lEMA = price[0];
        double MACD = 0.0;
        double prevMACD;
        double result = 0.0;
        int end = Math.min(Math.min(price.length,buyProfit.length),sellProfit.length);
        for (int i=1; i<end; i++) {
            sEMA = price[i]*sA + sEMA*(1-sA);
            lEMA = price[i]*lA + lEMA*(1-lA);
            prevMACD = MACD;
            MACD = (sEMA-lEMA)*aA + MACD*(1-aA);
            if (prevMACD<0 && MACD>0) {
                result += buyProfit[i]-0.005;
            }
            if (prevMACD>0 && MACD<0) {
                result += sellProfit[i]-0.005;
            }
        }
        return result;
    }

    public void train(String[] dataClass, String[] profitClass) {
        final int SA_MIN = 10;
        final int SA_MAX = 17280;
        final int LA_MIN = 10;
        final int LA_MAX = 37440;
        final int AA_MIN = 10;
        final int AA_MAX = 12960;

        final int EPOCH_NUM = 100;
        final int POPULATION = 500;

        final int TOURNAMENTS = POPULATION;
        final int MUTATIONS = (int) (POPULATION*0.5);
        final int CROSSOVERS = POPULATION;

        System.out.println("Start training of MACD Signal");

        //Prepare start population
        int[][] population = new int[POPULATION][3];
        for (int i=0; i<POPULATION; i++) {
            population[i][0] = ThreadLocalRandom.current().nextInt(SA_MIN, SA_MAX + 1);
            population[i][1] = ThreadLocalRandom.current().nextInt(Math.min(Math.max(LA_MIN,population[i][0]),LA_MAX), LA_MAX + 1);
            population[i][2] = ThreadLocalRandom.current().nextInt(AA_MIN, Math.max(Math.min(AA_MAX + 1, population[i][0]),AA_MIN+1));
        }

        ArrayList<HashMap<String, Double[]>> trainingSets = new ArrayList<>();
        StandardDeviation stdObj = new StandardDeviation();
        Mean meanObj = new Mean();

        int SETS_NUM = Math.min(dataClass.length, profitClass.length);
        for (int k=0; k<SETS_NUM; k++) {
            //Prepare training sets
            ArrayList<Double> pricesArray = new ArrayList<>();
            MongoCursor<Document> cursor = historyData
                    .find(eq("class", dataClass[k]))
                    .sort(Sorts.ascending("timestamp")).iterator();
            while (cursor.hasNext()) {
                pricesArray.add(cursor.next().getDouble("close"));
            }
            ArrayList<Double> buyProfitsArray = new ArrayList<>();
            ArrayList<Double> sellProfitsArray = new ArrayList<>();
            cursor = historyData
                    .find(eq("class", profitClass[k]))
                    .sort(Sorts.ascending("timestamp")).iterator();
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                buyProfitsArray.add(doc.getDouble("buyProfit"));
                sellProfitsArray.add(doc.getDouble("sellProfit"));
            }

            Double[] prices = pricesArray.toArray(new Double[pricesArray.size()]);
            Double[] buyProfits = buyProfitsArray.toArray(new Double[buyProfitsArray.size()]);
            Double[] sellProfits = sellProfitsArray.toArray(new Double[sellProfitsArray.size()]);
            HashMap<String,Double[]> set = new HashMap<>();
            set.put("prices", prices);
            set.put("buys", buyProfits);
            set.put("sells", sellProfits);
            set.put("mean", new Double[]{meanObj.evaluate(ArrayUtils.toPrimitive(prices))});
            trainingSets.add(set);
        }
        System.out.println("Training data loaded.");



        double[] profit = new double[POPULATION];
        double[] fitness = new double[POPULATION];
        int[] alphaSpecies = population[0];
        double alphaFitness = 0;
        double alphaProfit = 0;
        for (int epoch=0; epoch<EPOCH_NUM; epoch++ ) {
            //Update profit values
            for (int i=0; i<POPULATION; i++) {
                profit[i] = 0.0;
                for (int k=0; k<SETS_NUM; k++) {
                    profit[i] += testProfit(trainingSets.get(k).get("prices"),
                            trainingSets.get(k).get("buys"),
                            trainingSets.get(k).get("sells"),
                            population[i][0],
                            population[i][1],
                            population[i][2]);
                }
            }
            // fitness function calculation
            for (int i=0; i<POPULATION; i++) {
                fitness[i] = 0.0;
                for (int xI=-1; xI<1; xI++) {
                    for (int yI=-1; yI<1; yI++) {
                        for (int zI=-1; zI<1; zI++) {
                            int xP = (int)((1+xI*0.01)*population[i][0]);
                            int yP = (int)((1+yI*0.01)*population[i][1]);
                            int zP = (int)((1+zI*0.01)*population[i][2]);
                            for (int k=0; k<SETS_NUM; k++) {
                                fitness[i] += testProfit(trainingSets.get(k).get("prices"),
                                        trainingSets.get(k).get("buys"),
                                        trainingSets.get(k).get("sells"),
                                        xP, yP, zP)/9/
                                        trainingSets.get(k).get("mean")[0];
                            }
                        }
                    }
                }
            }

            int[][] survived = new int[POPULATION][3];
            // looking for alpha
            int alpha = 0;
            for (int i=0; i<POPULATION; i++) {
                if (fitness[i]>fitness[alpha]) alpha=i;
            }
            alphaSpecies = population[alpha];
            alphaFitness = fitness[alpha];
            alphaProfit = profit[alpha];

            // Tournament for surviving
            for (int i=0; i<POPULATION; i++) {
                int candidateOne = ThreadLocalRandom.current().nextInt(0, POPULATION);
                int candidateTwo = ThreadLocalRandom.current().nextInt(0, POPULATION);
                if (i<TOURNAMENTS) {
                    survived[i] = fitness[candidateOne] > fitness[candidateTwo] ? population[candidateOne] : population[candidateTwo];
                } else {
                    survived[i] = population[candidateOne];
                }
            }
            shuffleArray(survived,1);
            // Crossovers
            //int numCrossovers = CROSSOVERS*epoch/EPOCH_NUM;
            int numCrossovers = CROSSOVERS;
            int[][] newborn = new int[POPULATION][3];
            for (int i=0; i<POPULATION; i++) {
                if (i<numCrossovers) {
                    int candidateOne = ThreadLocalRandom.current().nextInt(0, POPULATION);
                    int candidateTwo = ThreadLocalRandom.current().nextInt(0, POPULATION);
                    double a = Math.random();
                    newborn[i][0] = Math.toIntExact(Math.round(survived[candidateOne][0]*a+survived[candidateTwo][0]*(1-a)));
                    newborn[i][0] = Math.max(SA_MIN,newborn[i][0]);
                    a = Math.random();
                    newborn[i][1] = Math.toIntExact(Math.round(Math.max(survived[candidateOne][1],newborn[i][0])*a+Math.max(survived[candidateTwo][1],newborn[i][0])*(1-a)));
                    newborn[i][1] = Math.max(LA_MIN,newborn[i][1]);
                    a = Math.random();
                    newborn[i][2] = Math.toIntExact(Math.round(Math.min(survived[candidateOne][2],newborn[i][0])*a+Math.min(survived[candidateTwo][2],newborn[i][0])*(1-a)));
                    newborn[i][2] = Math.max(AA_MIN,newborn[i][2]);
                } else {
                    newborn[i] = survived[i];
                }
            }
            shuffleArray(survived,1);
            // Mutations
            //int numMutations = MUTATIONS*(1-epoch/EPOCH_NUM);
            int numMutations = MUTATIONS;
            int[][] mutated = new int[POPULATION][3];
            double[] k1 = new double[POPULATION];
            double[] k2 = new double[POPULATION];
            double[] k3 = new double[POPULATION];
            for (int i=0; i<POPULATION; i++) {
                k1[i] = population[i][0];
                k1[i] = population[i][1];
                k1[i] = population[i][2];
            }
            double std1 = stdObj.evaluate(k1);
            double std2 = stdObj.evaluate(k2);
            double std3 = stdObj.evaluate(k3);
            double mutationRatio = 0.1;
            for (int i=0; i<POPULATION; i++) {
                if (i<numMutations) {
                    mutated[i][0] = (int) (ThreadLocalRandom.current().nextGaussian()*std1*mutationRatio+newborn[i][0]);
                    mutated[i][1] = (int) (ThreadLocalRandom.current().nextGaussian()*std2*mutationRatio+newborn[i][1]);
                    mutated[i][2] = (int) (ThreadLocalRandom.current().nextGaussian()*std3*mutationRatio+newborn[i][2]);
                    mutated[i][1] = mutated[i][1] > mutated[i][0] ? mutated[i][1] : mutated[i][0];
                    mutated[i][2] = mutated[i][2] < mutated[i][0] ? mutated[i][2] : mutated[i][0];
                } else {
                    mutated[i] = newborn[i];
                }
            }
            population = mutated;
            population[0] = alphaSpecies;
            System.out.printf("Epoch %d is completed. Found (%d, %d, %d) with total profit %f (fitness %f)%n", epoch, population[0][0],population[0][1],population[0][2],alphaProfit,alphaFitness);
        }
        System.out.printf("Found the best parameters (%d, %d, %d) with total profit %f%n", population[0][0],population[0][1],population[0][2],alphaProfit);
        for (int k=0; k<SETS_NUM; k++) {
            double p = testProfit(trainingSets.get(k).get("prices"),
                    trainingSets.get(k).get("buys"),
                    trainingSets.get(k).get("sells"),
                    population[0][0],
                    population[0][1],
                    population[0][2]);
            System.out.printf("Profit for %s is %f%n", dataClass[k],p);
        }
        //Alpha is found
        this.macdIndicator = new MACDIndicator(historyData,historyItem,population[0][0],population[0][1],population[0][2]);
        // Trailing stop 1.0%
        // (9822, 34818, 1653) with total profit 17,355000
        // (1370, 32873, 10991) with total profit 20,005000
        // (13173, 29243, 1129) with total profit 17,760000
        // (276, 997, 147) with total profit 17,570000 (mean 0,278200 deviation 4,353703)
        // (50, 14770, 17) with total profit 21,820000 (mean 0,190760 deviation 4,044192)
        // Trailing stop 0.5%
        // (340, 3284, 210) with total profit 18,355000
        // (34, 23125, 20) with total profit 30,065000
        // (15, 23317, 11) with total profit 27,865000
        // (37, 23942, 16) with total profit 25,745000
        // Trailing stop 2.0%
        // (1254, 10224, 236) with total profit 18,255000
        // (1074, 8714, 628) with total profit 18,950000
        // (1241, 10016, 228) with total profit 18,805000 and deviation 5,365417
    }


    static void shuffleArray(int[][] ar, int safe)
    {
        // If running on Java 6 or older, use `new Random()` on RHS here
        Random rnd = ThreadLocalRandom.current();
        for (int i = ar.length - 1; i > safe; i--)
        {
            int index = rnd.nextInt( i + 1 - safe) + safe;
            // Simple swap
            int[] a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

}
