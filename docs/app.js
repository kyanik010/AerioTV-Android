const movies=["The Last Horizon","Midnight Run","Blue Signal","Afterlight","Northbound","The Divide","Black Harbor","Eclipse"];
const series=["City Lines","Dark Matter","The District","Silent Code","The Bridge","Final Season","The Agency","Night Shift"];
function card(name,index){return '<div class="card" tabindex="0" data-name="'+name.replace(/"/g,'&quot;')+'"><div class="poster p'+index+'"><span>'+name+'</span></div><div class="card-title">'+name+'</div></div>'}
function render(id,names){document.getElementById(id).innerHTML=names.map(card).join("");document.querySelectorAll("#"+id+" .card").forEach(c=>c.addEventListener("click",()=>select(c.dataset.name)))}
function select(name){document.getElementById("heroTitle").textContent=name;document.getElementById("heroYear").textContent="2026";document.getElementById("heroPlot").textContent="Lumen cinematic home preview — the Android TV layout is mirrored from the current Compose implementation."}
render("movies",movies);render("series",series);
document.querySelectorAll(".nav-btn").forEach(b=>b.addEventListener("click",()=>{const t=b.dataset.target;if(t==="movies")document.getElementById("moviesShelf").scrollIntoView({behavior:"smooth"});if(t==="series")document.getElementById("seriesShelf").scrollIntoView({behavior:"smooth"});}));
document.getElementById("playBtn").addEventListener("click",()=>document.getElementById("heroTitle").textContent="Preview — Play");
document.querySelectorAll(".card").forEach(c=>c.addEventListener("focus",()=>select(c.dataset.name)));
