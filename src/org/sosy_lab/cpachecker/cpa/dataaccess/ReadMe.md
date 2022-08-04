# DataAccess 包下的主要工作
用于进行中断函数的数据冲突检测。

**Preview**  
如图所示 `ep` 与 `ec` 在同一函数中，`er` 在中断函数中。

![img_3.png](img_3.png)

程序主要入口在 `DataAccessTransferRelation` 中，传入 `节点` 和 `边` 以及 `共享变量` 的信息进行处理。

边信息将转化为一个状态，其形式如下：
```
如 main 函数中的边：
        11  x = 0   
其状态将转化为：
        e = (x,main,11,W)
其中：
e.Name = x
e.Task = main
e.Location = 11
e.Action = W
```

节点内包含 `DataAccess` 数据访问集，其形式如下：
```
对于共享变量 share_var = ['x','y','z'], 其数据访问集可为
DataAccess={
    'x':[('x','mian','11','W'),('x','main','15','R'),...],
    'y':[('y','main','12','R'),('y','isr1','26','W'),...],
    'z':[('z','main','13','R'),('z','isr2','36','W'),...],
    ...
}
```

节点内包含 `DataRace` 数据冲突集，其主要用于保存已检测出的数据冲突集，为一个三元组 `(ep,er,ec)`，如
```
DataRace = {
    'x':[('x','mian','11','W'),('x','isr','36','W'),('x','main',15,'R')],
    'y':[],
    ...
}
```

程序操作步骤（主要位于 `DataAccessTransferRelation`）：  

Step 1. 取出上次的的`DATAACCESS` [里面包含每个变量的访问集DataAccess和已经产生冲突的数据集DataRace]，获取边上的共享节点的信息 `edgeVtx`

Step 2. 如果边信息 `edgeVtx` 为空，则返回当前 `DATAACCESS`，否则转 Step3.

Step 3. 取出边上的读写信息，首先对读中节点进行数据冲突检测，其次对写中节点进行数据冲突检测。其步骤如下

    Step 3.1  若，当前节点已经被检测过了，即在DataRace中已经存在，则不再进行数据检测，返回上层调用

    Step 3.2  进行数据冲突检测，从 `DataAccess` 取出当前共享变量的操作集合 `ActionList` ，并将当前状态 `ec` 调用函数 `DataRace`进行数据冲突检测。  

    Step 3.3  判断 ActionList 里的操作数是否大于 2，大于2，转 Step 2.3， 否则转 Step 2.2  

    Step 3.4  将 ec 放入 ActionList 中， 返回上层调用。  

    Step 3.5  在 ActionList 中搜寻与 ec 在同一函数中的 ep，即 调用 get_ep

        > Step 3.5.1  初始化。令 ep 初始位置为 ep_position = -1， er 默认选取 ActionList 中的最后一个，并将 er 的操作（er.Action）放入 inter_operation，将 er 放入 inter_state 中  

        > Step 3.5.2 从 ActionList 的倒数第二个元素往前进行搜索，令当前搜索状态为 e， 则进行一下判断：  

        >             ① e.Task 是否与 ep.Task 相同， 与 er.Task 不同，  

        >                 满足，则令 ep_position=当前位置。  

        >                 不满足，转 ② 。  

        >             ② e.Action 是否在 inter_operation 中，  

        >                 在，获取 inter_operation 中该 Action 的位置 idx ，将 inter_state 中 idx 位置上的 state 换成 e 。  

        >                 不在，将 e.Action 存入 inter_operation, e 存入 inter_state 中  

        >             ③ 探索下一个 e，直到遍历完 ActionList 中所有的状态。  

    Step 3.6  当 ep_position == -1 时， 表示尚未进入中断或仍在中断中，则调用 UpdateDataAccess，并将 flag 置为2  

    Step 3.7  当 ep_position != -1 时， 表示可能会产生数据中断，我们将遍历 inter_operation 中的所有操作，同 ep,ec 中的操作所构成的三元组是否是 冲突模式集（PatternList） 中的一个  

        ① 是，则将该三元组放入 DataRace 中, 并更新 DataAccess，则调用 UpdateDataAccess，并将flag置为0.  

        ② 不是，则调用 UpdateDataAccess，并将flag置为1



数据访问集的更新（`UpdateDataAccess`）：

- 当 `flag = 0` 时（产生了数据冲突）：当前 `ActionList` 置空  
- 
- 当 `flag = 1` 时（出中断后，未产生数据冲突）：删除中断中的所有状态，及 `ep`，放入 `ec`  
- 
- 当 `flag = 2` 时（未进中断或未出中断，将 `ec` 放入 `ActionList` 中）  


对于路径的更新有三种情况：
1. 当前任务与上一任务在同一函数中，无需进行数据检测
2. 当前任务与上一任务不在同一函数中，但是曾经走过的函数，必须检测
3. 当前任务所在函数并未涉足，无需检测



## 一个问题
![img_2.png](img_2.png)

预设方法：

1. 是否可以让所有正常调用的函数多加一个一个主函数调用标识
2. 是否可以让所有的中断进行独特标记，以便区分。

# DataAccess 包下的主要内容

## 1. State

每个节点应该包含的内容，也就是说最基本的单元 `e=(Name,Task,Loaction,Action)`

**参数**

- `Name`：变量的名称  

- `Task`：变量所在的程序 —— 主函数和中断函数  

- `Location`：变量所在的行号 —— 需不需要都OK， 主要是供核对使用  

- `Action`：对变量的行为 —— 读(R)或者写(W)  

**方法**

- `构造方法`：需要传入上方四个参数  

- `to_string方法`：输出时使用

## 2. DataState

存放在数据访问集中的数据格式

**参数**

- `N`：Name，共享变量的名称

- `A`：Action，存放对共享变量的操作，是一个存放 `State` 的 `List`

**方法**

- `boolean isempty()`: 判断这个类是否是由 构造方法1 生成

- `DataState()`: 构造方法1， 不对参数进行初始化

- `DataState(N)`：构造方法2，生成 `DataAccess[N] = []` 的形式

- `DataState(N,e)`：构造方法3，生成 `DataAccess[N] = [e]` 的形式

- `void append(e)`：将 `State e` 放入 `DataAccess[N]` 中

## 3. Interruptinformation

用于标记 `DataAccess` 中找到的 `ep、er及相关操作`

**参数**
- `ep_position`：ep 的位置， 默认为-1（未找到）

- `inter_operation`：中断中对 共享变量 的 操作

- `inter_state`：中断中对 共享变量 的最新 操作状态 集合

**方法**
- `public Interruptinformation()` ：构造函数

- `public Integer getEp_position()` ：ep 的 get 方法

- `public void setEp_position(Integer ep_position)`：ep 的 set 方法

- `public List<String> getInter_operation()`：inter_operation 的 get 方法

- `public void setInter_operation(String inter_operation)`：inter_operation 的 set 方法

- `public List<State> getInter_state()` ：inter_state 的 get 方法

- `public void setInter_state(State inter_state)`：inter_state 的 set 方法

- `public int getindexInter_operation(String Action)` ：找到在 inter_operation 中与操作 Action 相同的位置





## 4. DataAccessState

数据访问集的直观形式

**参数**

- `DataAccess`: 数据访问集，形如 `DataAccess={'Name':[the action about Name]}` 的集合

- `DataRace`: 已产生的数据冲突对，形如 `DataRace={'Name':[ep,er,ec]}` 的集合

**方法**

- `public static DataAccessState getInitialInstance()`: DataAccessCPA中调用，具体工作不知

- `public DataAccessState()`：构造方法，对DataAccess 和 DataRace 进行初始化

- `public static List<DataState> getDataAccess()`：DataAccess 的 get 方法

- `public static List<DataState> getDataRace()`： DataRace 的 get 方法

- `public static void setDataRace(DataState dataRace)`：DataRace 的 set 方法

- `public int Is_exist(String Name)`：判断 Name 是否在 DataAceess 里面

- `public void add(DataState e)`: 将操作 e 放入 DataAccess 里面

- `public static List<State> action_list(String Name)`：对 Name 放回 DataAccess 中的操作，也即返回 DataAccess[N]


**@Override ： 重写方法**

- `public String getCPAName()` ：声明这个 CPA 的名字

- `public boolean checkProperty(String property) throws InvalidQueryException`： 不知用处，未实现

- `public Object evaluateProperty(String property) throws InvalidQueryException` ： 不知用处，未实现

- `public void modifyProperty(String modification) throws InvalidQueryException`： 不知用处，未实现

## 5. DataAccessCPA

告诉 `CPAchecker` 存在一个 `DataAccess` 的处理机制

**参数**

- `Configuration config`： 读取配置文件用的

- `LogManager logger`：不知道用处

- `ShutdownNotifier shutdownNotifier`：不知道用处

- `CFA cfa`：读取 CFA 用的

**方法**
- `public static CPAFactory factory()`：在 CPAchecker 注册本 CPA

- `public DataAccessCPA(Configuration pConfig, LogManager pLogger, ShutdownNotifier pShutdownNotifier, CFA pCfa)` ：构造方法，注册 DataRaceTransferRelation ？


**@Override ： 重写方法**

- `public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException`：调用 DataAccessState 中的 getInitialInstance()   不知用处


## 6. DataAccessTransferRelation

数据冲突的主要检测部分

**@Override ： 重写方法**

注：下方所有 `ActionList` 表示的是：针对某一变量的具体数据访问集

- `public Collection<DataAccessState> getAbstractSuccessorsForEdge(AbstractState state, Precision precision, CFAEdge cfaEdge) throws CPATransferException, InterruptedException`：用于获取本节点的信息，并通过cfaEdge中的信息构造下一个节点，  在实现此函数时问题极多，其主要原因在于不知道 state、cfaEdge 中的具体内容，等待将 CPAchecker 可调试时，估计会被解决，但其中仍有些问题可能需要进一步的讨论

- `public Collection<? extends AbstractState> strengthen(AbstractState state, Iterable<AbstractState> otherStates, @Nullable CFAEdge cfaEdge, Precision precision) throws CPATransferException, InterruptedException`：用于返回信息供其他 CPA 使用， 默认生成的未修改

- `public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pstate, Precision pPrecision) throws CPATransferException, InterruptedException`： 也是获取后继，但似乎用不着

**其他方法**

- `public DataState DataRace(List<State> ActionList, State ec)`：检测数据冲突的函数

- `public Interruptinformation get_ep(List<State> ActionList, String Task)`：得到 ep 的位置， 在 DataAccess 中搜寻 ep

- `public void UpdateDataAccess(List<State> ActionList, Integer ep_position, State ec, Integer flag)` ： 更新 DataAccess

- `private void delActionList(List<State> ActionList, Integer idx)`： 用于 删除 ActionList 


