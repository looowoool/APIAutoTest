package api.utils;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractBigDecimalAssert;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.Assertions;

import java.math.BigDecimal;


/**
 * assertJ json数据判断增强 eg:不提供提取数组的方法，在断言中作用比较小
 *
 * eg:AssertJSON.assertThat(response).jsonPathAsString.isEqualTo("xxx").as("比较xxx");
 *
 * lamda : eg:assertThat(fellowshipOfTheRing).filteredOn( character -> character.getName().contains("o") )
 *                                .containsOnly(aragorn, frodo, legolas, boromir);
 *
 * @author
 *
 */
public class AssertJSON extends AbstractAssert<AssertJSON, String> {
    protected AssertJSON(String actual) {
        super(actual, AssertJSON.class);
        // TODO Auto-generated constructor stub
    }

    public static AssertJSON assertThat(String json) {
        return new AssertJSON(json);
    }

    /**
     * 提取字符串节点
     */
    public AbstractCharSequenceAssert<?, String> jsonPathAsString(String path) {
        return Assertions.assertThat((String) JSONPath.eval(getJSON(actual), path));
    }

    /**
     * 提取boolean节点
     */

    public AbstractBooleanAssert<?> jsonPathAsBoolean(String path) {
        return Assertions.assertThat((boolean) JSONPath.eval(getJSON(actual), path));
    }



    /**
     * 提取数字节点
     *
     */
    public AbstractIntegerAssert<?> jsonPathAsInteger(String path) {
        return Assertions.assertThat((Integer) JSONPath.eval(getJSON(actual), path));
    }

    /**
     * 提取小数
     *
     */

    public AbstractBigDecimalAssert<?> jsonPathAsBigDecimal(String path) {
        return Assertions.assertThat((BigDecimal) JSONPath.eval(getJSON(actual), path));
    }

    private JSONObject getJSON(String json) {
        JSONObject j = new JSONObject();
        j = JSONObject.parseObject(json);
        return j;
    }

}
