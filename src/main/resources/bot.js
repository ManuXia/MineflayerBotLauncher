const mineflayer = require('mineflayer')
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder')
const mcDataLoader = require('minecraft-data')
const readline = require('readline')
const { Vec3 } = require('vec3')

const CONFIG = {
  host: '127.0.0.1', //IP放这里
  port: 25565, //端口放这里
  username: '7891', //玩家名字
  password: '277879113', //密码
  loginDelay: 3000, //输入延迟

  stuckCheckInterval: 500, //卡多少秒判定卡住
  stuckDistance: 0.03, //不需要知道

  jumpAttemptsBeforeReroute: 3, //跳跃尝试多少次
  jumpHoldTicks: 40, //没必要知道，是这样就对了

  maxStuckLevel: 3, //最高卡死等级
  maxRerouteRadius: 256 //最大寻路范围
}

const bot = mineflayer.createBot({
  host: CONFIG.host,
  port: CONFIG.port,
  username: CONFIG.username
})

bot.loadPlugin(pathfinder)

let mcData

let globalGoal = null
let localGoal = null

let lastPos = null
let stuckLevel = 0

let jumpQueue = 0
let jumpHold = 0

const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
const log = m => console.log(`[BOT] ${m}`)

bot.once('spawn', () => {
  mcData = mcDataLoader(bot.version)
  log('Spawned')

  setTimeout(() => bot.chat(`/l ${CONFIG.password}`), CONFIG.loginDelay)

  setupMovements()
  enableCLI()
  startStuckMonitor()
  startGoalWatchdog()
})

function setupMovements () {
  const m = new Movements(bot, mcData)

  m.allowParkour = true
  m.canSprint = true
  m.canDig = true
  m.canSwim = true
  m.canOpenDoors = true

  m.maxJump = 1
  m.maxDropDown = 4

  m.allowFreeMotion = false
  m.allow1by1towers = false

  bot.pathfinder.setMovements(m)
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

function forceJumpSequence () {
  if (jumpQueue > 0 || jumpHold > 0) return
  log('Force jump x3')
  jumpQueue = CONFIG.jumpAttemptsBeforeReroute
}

function startStuckMonitor () {
  setInterval(() => {
    if (!globalGoal) return
    if (!bot.pathfinder.isMoving()) return

    const pos = bot.entity.position

    if (lastPos && pos.distanceTo(lastPos) < CONFIG.stuckDistance) {
      stuckLevel++

      if (stuckLevel <= CONFIG.jumpAttemptsBeforeReroute) {
        forceJumpSequence()
        return
      }

      const effective = Math.min(stuckLevel, CONFIG.maxStuckLevel)
      const radius = Math.min(4 ** effective, CONFIG.maxRerouteRadius)

      const rx = pos.x + (Math.random() * radius * 2 - radius)
      const rz = pos.z + (Math.random() * radius * 2 - radius)

      localGoal = new Vec3(rx, pos.y, rz)

      log(`Reroute (${radius.toFixed(1)})`)
      bot.pathfinder.setGoal(
        new goals.GoalNear(rx, pos.y, rz, 1),
        true
      )
    } else {
      stuckLevel = 0
    }

    lastPos = pos.clone()
  }, CONFIG.stuckCheckInterval)
}

function startGoalWatchdog () {
  setInterval(() => {
    if (!globalGoal) return

    if (!bot.pathfinder.isMoving()) {
      log('Watchdog: resume global goal')
      bot.pathfinder.setGoal(
        new goals.GoalBlock(globalGoal.x, globalGoal.y, globalGoal.z),
        true
      )
    }
  }, 1000)
}

function enableCLI () {
  rl.on('line', line => {
    const [cmd, ...args] = line.trim().split(' ')
    if (cmd === 'goto') gotoCmd(args)
    if (cmd === 'yaw') yawCmd(args)
    if (cmd === 'action') actionCmd(args)
  })
}

function gotoCmd (args) {
  const [x, y, z] = args.map(Number)
  if ([x, y, z].some(isNaN)) return

  globalGoal = new Vec3(x, y, z)
  localGoal = null
  stuckLevel = 0
  lastPos = null

  log(`Goto ${x} ${y} ${z}`)
  bot.pathfinder.setGoal(
    new goals.GoalBlock(x, y, z),
    true
  )
}

function yawCmd (args) {
  const yaw = Number(args[0])
  if (!isNaN(yaw)) bot.look(yaw * Math.PI / 180, 0, true)
}

function actionCmd (args) {
  if (args[0] === 'right_click') bot.activateItem()
  if (args[0] === 'left_click') bot.swingArm('right')
}

bot.on('goal_reached', () => {
  if (localGoal && globalGoal) {
    localGoal = null
    log('Local done → resume global')
    bot.pathfinder.setGoal(
      new goals.GoalBlock(globalGoal.x, globalGoal.y, globalGoal.z),
      true
    )
  }
})

bot.on('death', () => {
  log('Died, clear goals')
  globalGoal = null
  localGoal = null
  stuckLevel = 0
})

bot.on('respawn', () => log('Respawned'))
bot.on('kicked', r => console.log('Kicked:', r))
bot.on('error', e => console.log('Error:', e))
