package api.configs;

//Java的实用工具类库java.util包

import api.utils.ReportUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Java XML解析工具 dom4j

public class ApiConfig {
	/*
	 * 解析接口测试配置xml文件类
	 * */

	//throws exception表示当某个方法可能会抛出某种异常时，声明可能抛出的异常，然后交给上层调用它的方法程序处理
	public ApiConfig(String configFilePath) throws DocumentException{
		SAXReader reader = new SAXReader();//创建saxReader io读取对象
		Document document = reader.read(configFilePath);//创建xml文档对象
		Element rootElement = document.getRootElement();//获取根元素
		//获取根路径
		rootUrl = rootElement.element("rootUrl").getTextTrim();//获取指定名称的对象的文本
		
		//获取post请求时参数列表
		@SuppressWarnings("unchecked")
        List<Element> paramElements = rootElement.element("params").elements(
				"param");
		paramElements.forEach((ele)->{
			params.put(ele.attributeValue("name").trim(),
					ele.attributeValue("value").trim());
		});
		//获取请求头参数列表
		@SuppressWarnings("unchecked")
        List<Element> headerElements = rootElement.element("headers").elements(
				"header");
		headerElements.forEach((ele)->{
			headers.put(ele.attributeValue("name").trim(),
					ele.attributeValue("value").trim());
		});
		//获取项目名称，非空时将将该值作为测试报告名称并调用测试报告类进行写入
		Element projectEle = rootElement.element("project_name");
		if(projectEle!=null){
			ReportUtil.setReportName(projectEle.getTextTrim());
		}
	}
	private String rootUrl;
	
	private Map<String,String> params = new HashMap<String, String>();
	
	private Map<String,String> headers = new HashMap<String, String>();

	//获取接口测试请求url
	public String getRootUrl() {
		return rootUrl;
	}

	//获取请求body参数键值对
	public Map<String, String> getParams() {
		return params;
	}

	//获取请求头键值对
	public Map<String, String> getHeaders() {
		return headers;
	}
	
}
