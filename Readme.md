# 本地聊天室服务器

这是一个基于Java Socket的本地聊天室应用，支持多用户实时聊天。

## 功能特性

- 🚀 多用户同时在线聊天
- 💬 实时消息广播
- 👥 在线用户列表显示
- 📱 响应式Web界面
- 🔄 消息历史记录
- 🎨 美观的用户界面

## 技术栈

- **后端**: Java 11 + Socket编程
- **前端**: HTML5 + CSS3 + JavaScript
- **数据处理**: Jackson JSON
- **构建工具**: Maven
- **代码简化**: Lombok

## 快速开始

### 1. 编译项目
```bash
mvn clean package
```

### 2. 运行服务器
```bash
java -jar target/chat-server-1.0-SNAPSHOT.jar
```

### 3. 访问聊天室
在浏览器中打开：`http://localhost:8999`

## 使用说明

1. **加入聊天室**: 首次访问时输入您的昵称
2. **发送消息**: 在输入框中输入消息，按回车或点击发送按钮
3. **查看在线用户**: 点击右上角的"在线用户"按钮
4. **消息历史**: 系统自动保存最近100条消息

## 项目结构

```
src/main/java/httpserver/
├── ChatServer.java          # 主服务器类
├── ChatHandler.java         # HTTP请求处理器
├── HttpMessageParser.java   # HTTP消息解析器
├── HttpTask.java           # 原始HTTP任务处理器
└── model/
    ├── ChatMessage.java    # 聊天消息模型
    ├── ChatUser.java       # 用户模型
    ├── Request.java        # HTTP请求模型
    └── Response.java       # HTTP响应模型
```

## API接口

- `GET /` - 聊天室主页面
- `POST /api/join` - 加入聊天室
- `POST /api/send` - 发送消息
- `GET /api/messages` - 获取消息历史
- `GET /api/users` - 获取在线用户列表

## 配置说明

- **端口**: 默认8999，可在`ChatServer.java`中修改
- **消息历史**: 最多保存100条消息
- **线程池**: 根据CPU核心数自动配置

## 注意事项

- 确保端口8999未被占用
- 支持Java 11及以上版本
- 建议在局域网内使用

## 许可证

Apache License 2.0