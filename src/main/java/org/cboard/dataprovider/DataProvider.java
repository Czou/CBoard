package org.cboard.dataprovider;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.googlecode.aviator.AviatorEvaluator;
import org.cboard.dataprovider.aggregator.Aggregatable;
import org.cboard.dataprovider.aggregator.InnerAggregator;
import org.cboard.dataprovider.annotation.DatasourceParameter;
import org.cboard.dataprovider.config.AggConfig;
import org.cboard.dataprovider.config.DimensionConfig;
import org.cboard.dataprovider.expression.NowFunction;
import org.cboard.dataprovider.result.AggregateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by zyong on 2017/1/9.
 */
public abstract class DataProvider {

    private InnerAggregator innerAggregator;
    protected Map<String, String> dataSource;
    protected Map<String, String> query;
    private int resultLimit;
    private long interval = 12 * 60 * 60; // second

    private static final Logger logger = LoggerFactory.getLogger(DataProvider.class);

    @DatasourceParameter(label = "Aggregate Provider", type = DatasourceParameter.Type.Checkbox, order = 100)
    private String aggregateProvider = "aggregateProvider";

    static {
        AviatorEvaluator.addFunction(new NowFunction());
    }

    private boolean isAggregateProviderActive() {
        String v = dataSource.get(aggregateProvider);
        return v != null && "true".equals(v);
    }

    /**
     * get the aggregated data by user's widget designer
     *
     * @return
     */
    public final AggregateResult getAggData(AggConfig ac, boolean reload) throws Exception {
        evalValueExpression(ac);
        if (this instanceof Aggregatable && isAggregateProviderActive()) {
            return ((Aggregatable) this).queryAggData(ac);
        } else {
            checkAndLoad(reload);
            return innerAggregator.queryAggData(ac);
        }
    }

    public final String getViewAggDataQuery(AggConfig config) throws Exception {
        evalValueExpression(config);
        if (this instanceof Aggregatable && isAggregateProviderActive()) {
            return ((Aggregatable) this).viewAggDataQuery(config);
        } else {
            return "Not Support";
        }
    }

    /**
     * Get the options values of a dimension column
     *
     * @param columnName
     * @return
     */
    public final String[][] getDimVals(String columnName, AggConfig config, boolean reload) throws Exception {
        String[][] dimVals = null;
        evalValueExpression(config);
        if (this instanceof Aggregatable && isAggregateProviderActive()) {
            dimVals = ((Aggregatable) this).queryDimVals(columnName, config);
        } else {
            checkAndLoad(reload);
            dimVals = innerAggregator.queryDimVals(columnName, config);
        }
        return dimVals;
    }

    public final String[] getColumn(boolean reload) throws Exception {
        String[] columns = null;
        if (this instanceof Aggregatable && isAggregateProviderActive()) {
            columns = ((Aggregatable) this).getColumn();
        } else {
            checkAndLoad(reload);
            columns = innerAggregator.getColumn();
        }
        return columns;
    }

    private void checkAndLoad(boolean reload) throws Exception {
        String key = getLockKey(dataSource, query);
        synchronized (key.intern()) {
            if (reload || !innerAggregator.checkExist()) {
                String[][] data = getData();
                innerAggregator.loadData(data, interval);
                logger.info("loadData {}", key);
            }
        }
    }

    private void evalValueExpression(AggConfig ac) {
        if (ac == null) {
            return;
        }
        Consumer<DimensionConfig> evaluator = (e) ->
                e.setValues(e.getValues().stream().map(v -> getFilterValue(v)).collect(Collectors.toList()));
        ac.getFilters().forEach(evaluator);
        ac.getColumns().forEach(evaluator);
        ac.getRows().forEach(evaluator);
    }

    private String getFilterValue(String value) {
        if (value == null || !(value.startsWith("{") && value.endsWith("}"))) {
            return value;
        }
        return AviatorEvaluator.compile(value.substring(1, value.length() - 1), true).execute().toString();
    }

    private String getLockKey(Map<String, String> dataSource, Map<String, String> query) {
        return Hashing.md5().newHasher().putString(JSONObject.toJSON(dataSource).toString() + JSONObject.toJSON(query).toString(), Charsets.UTF_8).hash().toString();
    }

    abstract public String[][] getData() throws Exception;

    public void setDataSource(Map<String, String> dataSource) {
        this.dataSource = dataSource;
    }

    public void setQuery(Map<String, String> query) {
        this.query = query;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

    public int getResultLimit() {
        return resultLimit;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void setInnerAggregator(InnerAggregator innerAggregator) {
        this.innerAggregator = innerAggregator;
    }

}
