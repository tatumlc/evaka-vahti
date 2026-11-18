# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT

"""Läsnäolon aloituksen ja päättymisen seuranta eVakan kirjauksista."""
from datetime import date, timedelta
from .core import get_calendar,settings,selected_id,state_file,read_state,write_state,notify

def collect(data, child_id):
    result={}
    for day in data.get('days',[]):
        d=day.get('date')
        for child in day.get('children',[]):
            if str(child.get('childId'))!=child_id:continue
            booked=[]
            for reservation in child.get('reservations') or []:
                r=reservation.get('range') or {}
                if r.get('start') and r.get('end'): booked.append(f"{r['start'][:5]}–{r['end'][:5]}")
            for att in child.get('attendances') or []:
                start=att.get('start')
                if not start:continue
                key=f'{d}:{start}'
                result[key]={'date':d,'start':start,'end':att.get('end'),'booked':booked}
    return result

def message(item, departure):
    if departure:
        title='eVaka-vahti: lähtö kirjattu'
        body=f"{item['date']}\nToteutunut {item['start'][:5]}–{item['end'][:5]}"
    else:
        title='eVaka-vahti: saapuminen kirjattu'
        body=f"{item['date']}\nSaapunut {item['start'][:5]}"
    return title,body+'\nVarattu: '+(', '.join(item['booked']) if item['booked'] else 'Ei varausta')

def run():
    cfg=settings();today=date.today()
    data=get_calendar(today-timedelta(days=7),today);cid=selected_id(cfg,data)
    if cid is None: print('Ei läsnäolomerkintöjä.');return
    current=collect(data,cid);path=state_file('lasnaolo.json');previous=read_state(path)
    if previous is None:
        write_state(path,current);print('Läsnäoloseuranta alustettu; vanhoista merkinnöistä ei lähetetä ilmoituksia.');return
    for key,item in sorted(current.items()):
        old=previous.get(key)
        if old is None:
            # Uudet kirjaukset voivat näkyä jälkikäteen: ilmoitus kuvaa rekisterimerkintää.
            title,body=message(item, bool(item.get('end')))
            notify(title,body)
        elif old.get('end') is None and item.get('end') is not None:
            title,body=message(item,True);notify(title,body)
    write_state(path,current)
    if not any(key not in previous or (previous[key].get('end') is None and current[key].get('end')) for key in current):
        print('Ei uusia saapumis- tai lähtömerkintöjä.')
