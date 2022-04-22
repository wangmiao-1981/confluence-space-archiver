# ConfluenceSpaceArchiver
一个全量归档confluence知识库的工具。通过登录、restapi的方式爬取知识库的结构，用jsoup将页面下载并解析成本地可以使用的离线版本。所有attachemnet、png、drawio都下载到本地。

## CHANGELOG
* 完成多线程版本，经过测试，异步线程取20-50左右比较好
* 修改了html中各标签的引入地址，变成相对路径

## TODO
* 更新时间做好了，要增加个更新模块
* 改成分布式的，即能更好控制，也可以并发爬取，要有个资源管理器，好上策略
* 增加低代码模式
* 队列增加重试机制

## 功能特性：

* 1.页面下载，转存本地
* 2.链接转写成本地可以跳转
* 3.资源文件（css,js,img）转至本地
* 4.附件下载
* 5.断点续爬，依赖checkpoint，最后一个页面会重爬
* 6.在页面中增加了attachments列表
* 7.重写了左侧的树形列表，增加可读性

## 逻辑架构

* 登录 - 解析需要下载的空间列表
* 登录指定空间 - 取cookie保持登录状态，给后续下载用
* 下载队列 - 按url解析资源文件：css、js、附件等
* 补充page间本地链接关系，替代书签

## 使用说明
* 配置数据库，用于保存页面信息，以后再考虑脱离数据库或用h2
* 先将application.yml中的配置改下： （是否爬取下级子页面） PROC_SUB_PAGE: true
（是否下载页面，先爬一个完整的页面树后，再开，可保障目录树的下级是完整的）  PROC_DOWNLOAD_PAGE: false
  
* 爬取结构
* 打开PROC_DOWNLOAD_PAGE: true，正式下载文件、附件