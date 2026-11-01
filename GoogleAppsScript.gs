/** Meal Manager sync bridge. Deploy this script as a Web App.
 * Keep APP_TOKEN private. Put the same value in the Android app's Sync screen.
 */
const SPREADSHEET_ID = '1-5X3oN-BW_MQLFROe5WgakP3AoLvBbnn0NeRzpsnL2Y';
const APP_TOKEN = 'CHANGE-THIS-TO-A-LONG-RANDOM-SECRET';
const DATA_SHEET = 'AppData';

function doGet(e) {
  try {
    if (!authorized_(e && e.parameter && e.parameter.token)) return json_({ok:false,message:'Unauthorized'});
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const sh = ss.getSheetByName(DATA_SHEET);
    if (!sh || !sh.getRange('A1').getValue()) return json_({ok:true,data:{members:[],months:{},settings:{endpoint:''}}});
    return json_({ok:true,data:JSON.parse(sh.getRange('A1').getValue())});
  } catch (err) { return json_({ok:false,message:String(err)}); }
}

function doPost(e) {
  try {
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    if (!authorized_(body.token)) return json_({ok:false,message:'Unauthorized'});
    if (body.action !== 'save' || !body.data || !body.data.members || !body.data.months) throw new Error('Invalid data payload');
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    let store = ss.getSheetByName(DATA_SHEET);
    if (!store) store = ss.insertSheet(DATA_SHEET);
    store.getRange('A1').setValue(JSON.stringify(body.data));
    store.hideSheet();
    updateVisibleSheet_(ss, body.data);
    return json_({ok:true,savedAt:new Date().toISOString()});
  } catch (err) { return json_({ok:false,message:String(err)}); }
}

function authorized_(token) { return !!APP_TOKEN && APP_TOKEN !== 'CHANGE-THIS-TO-A-LONG-RANDOM-SECRET' && token === APP_TOKEN; }
function json_(obj) { return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON); }

// Mirrors the selected month into the original workbook-style layout.
function updateVisibleSheet_(ss, allData) {
  const months = Object.keys(allData.months || {}).sort();
  if (!months.length) return;
  const monthKey = months[months.length - 1];
  const d = allData.months[monthKey];
  let sh = ss.getSheetByName('Sheet1') || ss.getSheets()[0];
  const members = allData.members || [];
  const totalExpense = (d.bazar || []).reduce((s,x)=>s+Number(x.amount||0),0);
  const totalMeals = (d.meals || []).reduce((s,x)=>s+Number(x.count||0),0);
  const rate = totalMeals ? totalExpense/totalMeals : 0;
  const days = Array.from({length:31},(_,i)=>i+1);
  sh.getRange('A1:E1').setValues([['SL','Depositor Name','Deposit amount','Remaining Balance','Meal cost']]);
  sh.getRange('G2:AN2').setValues([['SL','Name',...days,'Per person meal']]);
  // Clear prior values in the data area without changing cell formatting.
  sh.getRange('A2:E100').clearContent();
  sh.getRange('G3:AN100').clearContent();
  sh.getRange('B23:E100').clearContent();
  const memberRows = members.map((m,i)=>{
    const mealCount = (d.meals||[]).filter(x=>x.memberId===m.id).reduce((s,x)=>s+Number(x.count||0),0);
    const deposit = (d.deposits||[]).filter(x=>x.memberId===m.id).reduce((s,x)=>s+Number(x.amount||0),0);
    const cost = mealCount*rate;
    return {m,mealCount,deposit,cost,balance:deposit-cost};
  });
  if (memberRows.length) {
    sh.getRange(2,1,memberRows.length,5).setValues(memberRows.map((r,i)=>[i+1,r.m.name,r.deposit,r.balance,r.cost]));
    sh.getRange(3,7,memberRows.length,34).setValues(memberRows.map((r,i)=>{
      const counts = days.map(day=>{
        const date = monthKey+'-'+String(day).padStart(2,'0');
        return (d.meals||[]).filter(x=>x.memberId===r.m.id && x.date===date).reduce((s,x)=>s+Number(x.count||0),0);
      });
      return [i+1,r.m.name,...counts,r.mealCount];
    }));
  }
  sh.getRange(23,2,1,1).setValue('Bazar');
  sh.getRange(24,2,1,4).setValues([['SL','Date','Name','Amount']]);
  const bazar = d.bazar || [];
  if (bazar.length) sh.getRange(25,2,bazar.length,4).setValues(bazar.map((x,i)=>[i+1,x.date,x.name,Number(x.amount||0)]));
  const bazarTotalRow = 25 + bazar.length;
  sh.getRange(bazarTotalRow,4).setValue('Total');
  sh.getRange(bazarTotalRow,5).setValue(totalExpense);
  sh.getRange('K25').setValue('Meal Rate');
  sh.getRange('P25').setValue(rate);
  sh.getRange('AN2').setValue('Per person meal');
  sh.getRange('AN21').setValue(totalMeals);
}
