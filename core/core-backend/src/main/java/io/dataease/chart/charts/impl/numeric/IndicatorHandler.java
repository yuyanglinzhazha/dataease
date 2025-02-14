package io.dataease.chart.charts.impl.numeric;

import io.dataease.chart.utils.ChartDataBuild;
import io.dataease.engine.sql.SQLProvider;
import io.dataease.engine.trans.Dimension2SQLObj;
import io.dataease.engine.trans.Quota2SQLObj;
import io.dataease.engine.utils.Utils;
import io.dataease.extensions.datasource.dto.DatasourceRequest;
import io.dataease.extensions.datasource.dto.DatasourceSchemaDTO;
import io.dataease.extensions.datasource.model.SQLMeta;
import io.dataease.extensions.datasource.provider.Provider;
import io.dataease.extensions.view.dto.*;
import io.dataease.extensions.view.util.FieldUtil;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class IndicatorHandler extends NumericalChartHandler {
    @Getter
    private String render = "custom";
    @Getter
    private String type = "indicator";

    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, Provider provider) {
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = Utils.isCrossDs(dsMap);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var allFields = (List<ChartViewFieldDTO>) filterResult.getContext().get("allFields");
        ChartViewFieldDTO chartViewFieldDTO = yAxis.get(0);
        ChartFieldCompareDTO compareCalc = chartViewFieldDTO.getCompareCalc();
        boolean isYoy = org.apache.commons.lang3.StringUtils.isNotEmpty(compareCalc.getType())
                && !org.apache.commons.lang3.StringUtils.equalsIgnoreCase(compareCalc.getType(), "none");
        if (isYoy) {
            xAxis.clear();
            // 设置维度字段，从同环比中获取用户选择的字段
            xAxis.addAll(allFields.stream().filter(i-> StringUtils.endsWithIgnoreCase(i.getId().toString(),yAxis.get(0).getCompareCalc().getField().toString())).toList());
            xAxis.get(0).setSort("desc");
            if(StringUtils.endsWithIgnoreCase("month_mom",compareCalc.getType())){
                xAxis.get(0).setDateStyle("y_M");
            }
            if(StringUtils.endsWithIgnoreCase("day_mom",compareCalc.getType())){
                xAxis.get(0).setDateStyle("y_M_d");
            }
            if(StringUtils.endsWithIgnoreCase("year_mom",compareCalc.getType())){
                xAxis.get(0).setDateStyle("y");
            }
        }
        Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams());
        Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams());
        String querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
        querySql = provider.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
        datasourceRequest.setQuery(querySql);
        logger.debug("indicator chart sql: " + querySql);
        List<String[]> data = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
        boolean isdrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        quickCalc(xAxis, yAxis, data);
        Map<String, Object> result = ChartDataBuild.transNormalChartData(xAxis, yAxis, view, data, isdrill);
        T calcResult = (T) new ChartCalcDataResult();
        calcResult.setData(result);
        calcResult.setContext(filterResult.getContext());
        calcResult.setQuerySql(querySql);
        calcResult.setOriginData(data);
        return calcResult;
    }
}
