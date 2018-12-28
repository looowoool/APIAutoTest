package test.api;

import com.alibaba.fastjson.JSON;
import api.beans.ApiDataBean;
import api.configs.ApiConfig;
import api.excepions.ErrorRespStatusException;
import api.listeners.AutoTestListener;
import api.listeners.RetryListener;
import api.utils.*;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.dom4j.DocumentException;
import org.testng.ITestContext;
import org.testng.annotations.*;
import org.testng.annotations.Optional;
import static org.assertj.core.api.Assertions.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;

@Listeners({ AutoTestListener.class, RetryListener.class })
public class ApiTest extends TestBase {

	/**
	 * api请求跟路径
	 */
	private static String rootUrl;

	/**
	 * 跟路径是否以‘/’结尾
	 */
	private static boolean rooUrlEndWithSlash = false;

	/**
	 * 所有公共header，会在发送请求的时候添加到http header上
	 */
	private static Header[] publicHeaders;

	private static ApiConfig apiConfig;
	
	//所有api测试用例数据
	protected List<ApiDataBean> dataList = new ArrayList<ApiDataBean>();

	//http请求客户端对象
	private static HttpClient client;


	/**
	 * 初始化测试数据
	 * 
	 * @throws ErrorRespStatusException
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws UnsupportedEncodingException
	 * @throws Exception
	 */
	//测试配置文件名称
	@Parameters("envName")
	@BeforeSuite
	public void init(@Optional("api-config") String envName)
			throws UnsupportedEncodingException, ClientProtocolException,
            IOException, ErrorRespStatusException, Exception {
		//测试运行环境的配置文件的完整路径
		String configFilePath = Paths.get(System.getProperty("user.dir"),
				envName + ".xml").toString();
		ReportUtil.log("api config path:" + configFilePath);
		apiConfig = new ApiConfig(configFilePath);
		// 获取接口请求地址
		rootUrl = apiConfig.getRootUrl();
		rooUrlEndWithSlash = rootUrl.endsWith("/");//判断接口请求地址是否以“/”结尾

		// 读取 param，并将值保存到公共数据map
		Map<String, String> params = apiConfig.getParams();
		setSaveDates(params);

		//读取header，并将值添加到所以公共请求头
		List<Header> headers = new ArrayList<Header>();
		apiConfig.getHeaders().forEach((key, value) -> {
			Header header = new BasicHeader(key, value);
			headers.add(header);
		});
		publicHeaders = headers.toArray(new Header[headers.size()]);
		
		//发起https请求
		client = new SSLClient();//返回https请求对象
		client.getParams().setParameter(
				CoreConnectionPNames.CONNECTION_TIMEOUT, 60000);//请求超时
		client.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 60000);//读取超时
	}

	/*
	 * 调用testNG定义的参数后执行Excel数据读取，excelPath代表的文件的系统路径和名称（在TestBase中自动
	 * 加上项目路径，以形成完整的文件路径），sheetName代表的表名
	 * */
	@Parameters({ "excelPath", "sheetName" })
	@BeforeTest
	//@Optional代表找不到对应的参数时赋予的默认值
	public void readData(@Optional("") String excelPath,
			@Optional("") String sheetName) throws DocumentException {
		dataList = readExcelData(ApiDataBean.class, excelPath.split(";"),
				sheetName.split(";"));
	}

	/**
	 * 过滤数据，run标记为Y的执行。
	 * 
	 * @return
	 * @throws DocumentException
	 */
	@DataProvider(name = "apiDatas")
	public Iterator<Object[]> getApiData(ITestContext context)
			throws DocumentException {
		List<Object[]> dataProvider = new ArrayList<Object[]>();
		for (ApiDataBean data : dataList) {
			if (data.isRun()) {
				dataProvider.add(new Object[] { data });
			}
		}
		return dataProvider.iterator();
	}

	@Test(dataProvider = "apiDatas")
	public void apiTest(ApiDataBean apiDataBean) throws Exception {
		if (apiDataBean.getSleep() > 0) {
			// sleep休眠时间大于0的情况下进行暂停休眠
			ReportUtil.log(String.format("sleep %s seconds",
					apiDataBean.getSleep()));
			Thread.sleep(apiDataBean.getSleep() * 1000);
		}
		String apiParam = buildRequestParam(apiDataBean);
		// 封装请求方法
		HttpUriRequest method = parseHttpRequest(apiDataBean.getUrl(),
				apiDataBean.getMethod(), apiParam);

		String responseData;
		try {
			// 执行
			HttpResponse response = client.execute(method);
			int responseStatus = response.getStatusLine().getStatusCode();
			if (StringUtil.isNotEmpty(apiDataBean.getStatus())) {
				String apiStatus = apiDataBean.getStatus().trim();
				apiStatus = apiStatus.substring(0,3);
				assertThat(responseStatus).isNotEqualTo(apiStatus).as("返回状态码与预期不符合!");
//				Assert.assertEquals(responseStatus+"",apiStatus,
//						"返回状态码与预期不符合!");
			} else {
				// 非2开头状态码为异常请求，抛异常后会进行重跑
				if (200 > responseStatus || responseStatus >= 400) {
					throw new ErrorRespStatusException("返回状态码异常："
							+ responseStatus);
				}
				// 300+重定向数据获取
				if (300 >= responseStatus || responseStatus < 400) {

				}

			}
			HttpEntity respEntity = response.getEntity();
			Header respContentType = response.getFirstHeader("Content-Type");
			if (respContentType != null
					&& respContentType.getValue().contains("download") || respContentType.getValue().contains("octet-stream")) {
				String conDisposition = response.getFirstHeader(
						"Content-disposition").getValue();
				String fileType = conDisposition.substring(
						conDisposition.lastIndexOf("."),
						conDisposition.length());
				String filePath = "download/" + RandomUtil.getRandom(8, false)
						+ fileType;
				InputStream is = response.getEntity().getContent();
//				Assert.assertTrue(FileUtil.writeFile(is, filePath), "下载文件失败。");
				assertThat(FileUtil.writeFile(is, filePath)).isFalse().as( "下载文件失败。");
				// 将下载文件的路径放到{"filePath":"xxxxx"}进行返回
				responseData = "{\"filePath\":\"" + filePath + "\"}";
			} else {
//				responseData = DecodeUtil.decodeUnicode(EntityUtils
//						.toString(respEntity));
				responseData=EntityUtils.toString(respEntity, "UTF-8");
			}
		} catch (Exception e) {
			throw e;
		} finally {
			method.abort();
		}
		// 输出返回数据log
		ReportUtil.log("resp:" + responseData);
		// 验证预期信息
		verifyResult(responseData, apiDataBean.getVerify(),
				apiDataBean.isContains());

		// 对返回结果进行提取保存。
		saveResult(responseData, apiDataBean.getSave());
	}

	private String buildRequestParam(ApiDataBean apiDataBean) {
		// 分析处理预参数 （函数生成的参数）
		String preParam = buildParam(apiDataBean.getPreParam());
		savePreParam(preParam);// 保存预存参数 用于后面接口参数中使用和接口返回验证中
		// 处理参数
		String apiParam = buildParam(apiDataBean.getParam());
		return apiParam;
	}

	/**
	 * 封装请求方法
	 * 
	 * @param url
	 *            请求路径
	 * @param method
	 *            请求方法
	 * @param param
	 *            请求参数
	 * @return 请求方法
	 * @throws UnsupportedEncodingException
	 */
	private HttpUriRequest parseHttpRequest(String url, String method,
                                            String param) throws UnsupportedEncodingException {
		// 处理url
		url = parseUrl(url);
		ReportUtil.log("method:" + method);
		ReportUtil.log("url:" + url);
		ReportUtil.log("param:" + param.replace("\r\n", "").replace("\n", ""));
		HttpEntity entity = new StringEntity(param, "UTF-8");

		if ("post".equalsIgnoreCase(method)) {
			// 封装post方法
			HttpPost postMethod = new HttpPost(url);
			postMethod.setHeaders(publicHeaders);
			postMethod.setEntity(entity);
			return postMethod;
		}  else if("get".equalsIgnoreCase(method)){
			// 封装get方法
			HttpGet getMethod = new HttpGet(url);
			getMethod.setHeaders(publicHeaders);
			return getMethod;
		}else if ("put".equalsIgnoreCase(method)){
			// 封装put方法
			HttpPut putMethod = new HttpPut(url);
			putMethod.setHeaders(publicHeaders);
			putMethod.setEntity(entity);
			return putMethod;
		}else if ("upload".equalsIgnoreCase(method)) {
			HttpPost postMethod = new HttpPost(url);
			@SuppressWarnings("unchecked")
			Map<String, String> paramMap = JSON.parseObject(param,
					HashMap.class);
			MultipartEntity mEntity = new MultipartEntity();
			for (String key : paramMap.keySet()) {
				String value = paramMap.get(key);
				Matcher m = funPattern.matcher(value);
				if (m.matches() && m.group(1).equals("bodyfile")) {
					value = m.group(2);
					mEntity.addPart(key, new FileBody(new File(value)));
				} else {
					mEntity.addPart(key, new StringBody(paramMap.get(key)));
				}
			}
			postMethod.setEntity(entity);
			return postMethod;
		}else {
//			封装delete方法
			HttpDelete deleteMethod = new HttpDelete(url);
			deleteMethod.setHeaders(publicHeaders);
			return deleteMethod;
		}
	}

	/**
	 * 格式化url,替换路径参数等。
	 * 
	 * @param shortUrl
	 * @return
	 */
	private String parseUrl(String shortUrl) {
		// 替换url中的参数
		shortUrl = getCommonParam(shortUrl);
		if (shortUrl.startsWith("http")) {
			return shortUrl;
		}
		if (rooUrlEndWithSlash == shortUrl.startsWith("/")) {
			if (rooUrlEndWithSlash) {
				shortUrl = shortUrl.replaceFirst("/", "");
			} else {
				shortUrl = "/" + shortUrl;
			}
		}
		return rootUrl + shortUrl;
	}

}

