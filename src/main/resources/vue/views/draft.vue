<template id="draft">
    <app-frame>
    <div v-if="!finished">
    <div v-if="turn"><b>Round {{ round }}: Your Turn!!</b></div>
    <div v-if="!turn">Round {{ round }}: Not Your Turn</div>
    </div>
    <div v-if="finished"><b>Drafting Finished!</b></div>
        <section>
        <table id="grid">
            <tr>
                <td width='10%'></td>
                <td width='30%'><button @click="col1" :disabled="!turn">Take Column</button></td>
                <td width='30%'><button @click="col2" :disabled="!turn">Take Column</button></td>
                <td width='30%'><button @click="col3" :disabled="!turn">Take Column</button></td>
            </tr>
            <tr>
                <td width='10%'><button @click="row1" :disabled="!turn">Take Row</button></td>
                <td width='30%'><img class="grid" :src='grid[0]' @mouseover="zoom = grid[0]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
                <td width='30%'><img class="grid" :src='grid[1]' @mouseover="zoom = grid[1]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
                <td width='30%'><img class="grid" :src='grid[2]' @mouseover="zoom = grid[2]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
            </tr>
            <tr>
                <td width='10%'><button @click="row2" :disabled="!turn">Take Row</button></td>
                <td width='30%'><img class="grid" :src='grid[3]' @mouseover="zoom = grid[3]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
                <td width='30%'><img class="grid" :src='grid[4]' @mouseover="zoom = grid[4]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
                <td width='30%'><img class="grid" :src='grid[5]' @mouseover="zoom = grid[5]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
            </tr>
            <tr>
                <td width='10%'><button @click="row3" :disabled="!turn">Take Row</button></td>
                <td width='30%'><img class="grid" :src='grid[6]' @mouseover="zoom = grid[6]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
                <td width='30%'><img class="grid" :src='grid[7]' @mouseover="zoom = grid[7]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
                <td width='30%'><img class="grid" :src='grid[8]' @mouseover="zoom = grid[8]" @mouseout="zoom = '/img/back.jpg'" width="100%"/></td>
            </tr>
        </table>
        <div>
            <img :src="zoom" height="510" width="360" />
            <div v-if="status === 'connected'">
                <form @submit.prevent="sendMessage" action="#">
                    <input v-model="message"><button type="submit">Send Message</button>
                </form>
                <ul id="logs">
                    <li v-for="log in logs" class="log">
                        {{ log }}
                    </li>
                </ul>
            </div>
        </div>
        </section>
        <h2>Your Drafted Cards</h2>
        <section class="mycards">
          <span v-for="card in cards">
            <img :src="card" class="mycard" />
          </span>
        </section>
        <textarea rows="20" cols="80" v-model="cardlist_text"></textarea>
    </app-frame>
</template>
<script>
Vue.component("draft", {
template: "#draft",
  data: () => ({
    message: "",
    logs: [],
    status: "disconnected",
    round: 0,
    turn: false,
    grid: [],
    cards: [],
    cardlist_text: "",
    finished: false,
    zoom: "/img/back.jpg",
  }),
  mounted: function() {
    console.log("mounted");
    this.connect();
    window.addEventListener("resize", this.resize);
    this.resize();
  },
  destroyed: function() {
    window.removeEventListener("resize", this.resize);
  },
  methods: {
    resize() {
      height = window.innerHeight - 150;
      width = window.innerWidth - 200;
      max_width = width;
      max_height = height / 1.39;
      max = Math.min(max_width, max_height);
      document.getElementById('grid').style.maxWidth = max + 'px';
    },
    col1() { this.take("col1"); },
    col2() { this.take("col2"); },
    col3() { this.take("col3"); },
    row1() { this.take("row1"); },
    row2() { this.take("row2"); },
    row3() { this.take("row3"); },
    take(it) { this.socket.send(JSON.stringify(["take", it])); },
    connect() {
      console.log("connect!");
      this.socket = new WebSocket(location.protocol.replace("http", "ws") + "//" + location.hostname + ":" + location.port + "/ws");
      this.socket.onopen = () => {
        this.status = "connected";
        this.logs.push("Connected")

        this.socket.send(JSON.stringify(["join", this.$javalin.pathParams["session"]]));
        this.socket.onmessage = ({data}) => {
          json = JSON.parse(data);
          console.log(json);
          if (json[0] == "chat") {
            this.logs.push(json[1]);
            if (this.logs.length > 20) {
              this.logs.shift();
            }
          } else if (json[0] == "grid") {
            this.grid = json[1];
          } else if (json[0] == "turn") {
            this.round = json[1][0];
            this.turn = json[1][1];
            this.finished = json[1][2];
          } else if (json[0] == "cards") {
            this.cards = json[1];
          } else if (json[0] == "cards_text") {
            this.cardlist_text = json[1].join("\n");
          }
        };
      };
    },
    disconnect() {
      this.socket.close();
      this.status = "disconnected";
      this.logs = [];
    },
    sendMessage(e) {
      this.socket.send(JSON.stringify(["chat", this.message]));
      this.message = "";
    }
  }
});
</script>
<style>
    .hello-world {
        color: goldenrod;
    }
    img.grid {
      transition: transform .2s;
    }
    img.grid:hover {
      /* transform: scale(1.5); */
    }
    #grid {
      max-width: 500px;
      padding: 10px;
    }
    section {
      display: flex;
    }
    section.mycards {
      flex-wrap: wrap;
    }
    img.mycard {
      height: 300px;
      padding: 3px;
    }
    img.mycard:hover {
      /* transform: scale(1); */
    }
</style>
