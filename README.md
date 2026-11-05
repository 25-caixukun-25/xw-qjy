# 启智研学堂

面向职业技能培训的在线教育平台，以售卖在线技能课程为核心业务，配套学习计划、进度统计、积分排行榜、问答互动与点赞收藏等学习辅助能力，通过签到、积分与榜单等正向反馈机制提升学员的完课率与持续学习意愿。课程、章节与学习任务在后台统一配置。

## 技术栈

- **基础框架**：Spring Boot 2.7.2 / Spring Cloud 2021.0.3 / Spring Cloud Alibaba 2021.0.1.0
- **数据层**：MyBatis-Plus 3.4.3 / MySQL 8.0.23
- **缓存与锁**：Redis / Redisson 3.13.6
- **消息与任务**：RabbitMQ / XXL-Job 2.3.1
- **服务治理**：Nacos / OpenFeign / Seata 1.5.1
- **其他**：Elasticsearch 7.12.1 / Swagger / JDK 11

## 模块结构

| 模块 | 说明 |
|---|---|
| tj-common | 公共组件（分布式锁、MQ 工具、通用配置） |
| tj-api | Feign 接口定义与 DTO |
| tj-gateway | 网关服务 |
| tj-auth | 认证服务 |
| tj-user | 用户服务 |
| tj-course | 课程服务 |
| tj-learning | 学习中心（学习进度、积分） |
| tj-remark | 点赞服务 |
| tj-exam | 考试服务 |
| tj-media | 媒资服务 |
| tj-message | 消息服务 |
| tj-pay | 支付服务 |
| tj-trade | 交易服务 |
| tj-search | 搜索服务 |
| tj-data | 数据服务 |
| tj-promotion | 营销/促销服务 |

## 核心亮点

1. **学习进度秒级续播**：Redis 合并写 + 定时批量落库，读侧优先命中缓存，避免高频上报打库造成的写放大。
2. **通用分布式锁组件**：Redisson + AOP + 注解的通用组件，业务侧标 `@Lock` 即加锁、零侵入，支持锁类型与加锁策略切换，锁名由 SpEL 解析入参动态生成。
3. **积分与赛季排行榜**：当前赛季以 Redis 有序集合实时更新排名，赛季切换只换榜单 Key，历史赛季落库分表持久化。
4. **点赞服务独立拆分**：独立点赞服务承接写流量，定时增量扫描 + RabbitMQ 通知业务方回填计数，与课程主链路解耦。

## 快速开始

**环境要求**：JDK 11、Maven 3.6+、MySQL 8.0、Redis、RabbitMQ、Nacos、XXL-Job

1. 启动基础设施（MySQL、Redis、RabbitMQ、Nacos、XXL-Job）。
2. 导入数据库初始化脚本。
3. 修改各模块 `bootstrap.yml` 中的 Nacos 服务地址。
4. 依次启动网关（tj-gateway）与各业务服务。

> 注：`bootstrap-dev.yml` / `bootstrap-local.yml` 为本地环境配置，含内网地址，已在 `.gitignore` 中排除，请按需自行创建。
