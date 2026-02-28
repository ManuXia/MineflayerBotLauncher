const mineflayer = require('mineflayer')
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder')
const mcDataLoader = require('minecraft-data')
const readline = require('readline')
const { Vec3 } = require('vec3')

const CONFIG = {
  host: '127.0.0.1',
  port: 25565,
  username: '7891',
  password: '277879113',
  version: 'auto', //可选版本，比如1.21.11
  loginDelay: 3000,

  stuckCheckInterval: 600,
  stuckDistanceThreshold: 0.05,
  maxStuckCount: 5,

  jumpAttempts: 3,
  jumpHoldTicks: 35,

  maxRerouteRadius: 180,
  rerouteCooldown: 8000
}

const bot = mineflayer.createBot({
  host: CONFIG.host,
  port: CONFIG.port,
  username: CONFIG.username,
  version: CONFIG.version
})

bot.loadPlugin(pathfinder)

let mcData
let targetGoal = null
let tempGoal = null

let lastPosition = null
let stuckCount = 0
let lastRerouteTime = 0

let jumpQueue = 0
let jumpHold = 0

const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
const log = (msg) => console.log(`[BOT] ${msg}`)

bot.once('spawn', () => {
  mcData = mcDataLoader(bot.version)
  log('机器人已生成')

  setTimeout(() => {
    bot.chat(`/l ${CONFIG.password}`)
    log('已发送登录命令')
  }, CONFIG.loginDelay)

  setupMovements()
  setupEventListeners()
  enableCLI()
  startStuckMonitor()
})

function setupMovements() {
  const movements = new Movements(bot, mcData)

  movements.allowParkour = true
  movements.canSprint = true
  movements.canDig = true
  movements.canSwim = true
  movements.canOpenDoors = true
  movements.maxDropDown = 4
  movements.maxJump = 1.2

  bot.pathfinder.setMovements(movements)
  log('Movements 已配置')
}

function setupEventListeners() {
  bot.on('goal_reached', () => {
    if (tempGoal) {
      log('临时绕路完成')
      tempGoal = null
      if (targetGoal) {
        bot.pathfinder.setGoal(new goals.GoalNear(targetGoal.x, targetGoal.y, targetGoal.z, 1.5), true)
      }
    } else if (targetGoal) {
      log(`已到达目标: ${targetGoal.x.toFixed(1)} ${targetGoal.y.toFixed(1)} ${targetGoal.z.toFixed(1)}`)
      targetGoal = null
    }
  })

  bot.on('death', () => {
    log('Bot死亡，已清空目标')
    resetGoals()
  })

  bot.on('respawn', () => log('机器人重生'))
  bot.on('kicked', reason => log(`被踢出服务器: ${reason}`))
  bot.on('error', err => log(`错误: ${err.message}`))
}

bot.on('physicsTick', () => {
  if (jumpHold > 0) {
    bot.setControlState('jump', true)
    jumpHold--
    return
  }
  if (jumpQueue > 0) {
    jumpQueue--
    jumpHold = CONFIG.jumpHoldTicks
    return
  }
  bot.setControlState('jump', false)
})

function forceJump() {
  if (jumpQueue > 0 || jumpHold > 0) return
  jumpQueue = CONFIG.jumpAttempts
  log('强制跳跃序列')
}

function startStuckMonitor() {
  setInterval(() => {
    if (!targetGoal || !bot.pathfinder.isMoving()) return

    const pos = bot.entity.position.clone()

    if (lastPosition && pos.distanceTo(lastPosition) < CONFIG.stuckDistanceThreshold) {
      stuckCount++

      if (stuckCount >= 2 && stuckCount <= CONFIG.maxStuckCount) {
        forceJump()
      }

      if (stuckCount >= CONFIG.maxStuckCount) {
        const now = Date.now()
        if (now - lastRerouteTime < CONFIG.rerouteCooldown) return

        performReroute(pos)
        lastRerouteTime = now
        stuckCount = 0
      }
    } else {
      stuckCount = 0
    }

    lastPosition = pos
  }, CONFIG.stuckCheckInterval)
}

function performReroute(currentPos) {
  const radius = Math.min(80 + stuckCount * 20, CONFIG.maxRerouteRadius)
  
  const rx = currentPos.x + (Math.random() * radius * 2 - radius)
  const rz = currentPos.z + (Math.random() * radius * 2 - radius)
  const ry = currentPos.y + 2

  tempGoal = new Vec3(rx, ry, rz)

  log(`严重卡住 → 执行随机绕路 (半径 ${radius.toFixed(0)})`)
  bot.pathfinder.setGoal(new goals.GoalNear(rx, ry, rz, 2), true)
}

function resetGoals() {
  targetGoal = null
  tempGoal = null
  stuckCount = 0
  lastPosition = null
  bot.pathfinder.setGoal(null)
}

function enableCLI() {
  rl.on('line', (input) => {
    const args = input.trim().split(/\s+/)
    const cmd = args.shift()?.toLowerCase()

    switch (cmd) {
      case 'goto': handleGoto(args); break
      case 'look': handleLook(args); break
      case 'stop': handleStop(); break
      case 'action': handleAction(args); break
      case 'status': showStatus(); break
      default:
        if (cmd) log(`未知命令: ${cmd}。支持: goto, look, stop, action, status`)
    }
  })
}

function handleGoto(args) {
  if (args.length < 3) return log('用法: goto <x> <y> <z>')
  const [x, y, z] = args.map(Number)
  if ([x, y, z].some(isNaN)) return log('坐标必须是数字')

  resetGoals()
  targetGoal = new Vec3(x, y, z)

  log(`前往目标 → ${x} ${y} ${z}`)
  bot.pathfinder.setGoal(new goals.GoalNear(x, y, z, 1.8), true)
}

function handleLook(args) {
  if (args.length === 0) {
    const yaw = (bot.entity.yaw * 180 / Math.PI).toFixed(1)
    const pitch = (bot.entity.pitch * 180 / Math.PI).toFixed(1)
    return log(`当前朝向: yaw=${yaw}° pitch=${pitch}°`)
  }

  const yaw = parseFloat(args[0])
  const pitch = args[1] !== undefined ? parseFloat(args[1]) : 0

  if (isNaN(yaw)) return log('用法: look <yaw> [pitch] （单位：度）')

  bot.look(yaw * Math.PI / 180, pitch * Math.PI / 180, true)
  log(`视角已调整 → yaw=${yaw}° pitch=${pitch}°`)
}

function handleStop() {
  bot.pathfinder.setGoal(null)
  bot.setControlState('forward', false)
  bot.setControlState('jump', false)
  resetGoals()
  log('已停止所有移动')
}

function handleAction(args) {
  const act = args[0]?.toLowerCase()
  if (act === 'right' || act === 'right_click') {
    bot.activateItem()
    log('右键物品')
  } else if (act === 'left' || act === 'left_click' || act === 'swing') {
    bot.swingArm('right')
    log('左键挥动')
  } else {
    log('用法: action <right|left>')
  }
}

function showStatus() {
  const pos = bot.entity.position
  log(`位置: ${pos.x.toFixed(1)}, ${pos.y.toFixed(1)}, ${pos.z.toFixed(1)}`)
  log(`正在移动: ${bot.pathfinder.isMoving()}`)
  log(`主目标: ${targetGoal ? targetGoal.toString() : '无'}`)
  log(`临时绕路: ${tempGoal ? '是' : '无'}`)
  log(`卡住计数: ${stuckCount}`)
}

process.on('SIGINT', () => {
  log('正在关闭机器人...')
  bot.quit()
  rl.close()
  process.exit(0)
})
