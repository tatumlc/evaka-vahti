# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT

"""Hoitoaikavarausten muutokset ja vuorokauden yli ulottuvat varaukset."""
from datetime import date, timedelta
from .core import get_calendar, settings, selected_id, state_file, read_state, write_state, notify

def collect(data, child_id):
    items = {}
    for day in data.get("days", []):
        d = day.get("date")
        if not d: continue
        for child in day.get("children", []):
            if str(child.get("childId")) != child_id: continue
            reserved = [{"type": r.get("type"), "range": r.get("range")} for r in child.get("reservations", [])]
            items[d] = reserved
    return items

def as_text(reservations):
    if not reservations:return "Ei varausta"
    result=[]
    for r in reservations:
        span=r.get("range")
        result.append(f"{span['start'][:5]}–{span['end'][:5]}" if isinstance(span,dict) and span.get('start') and span.get('end') else 'Varaus ilman kellonaikoja')
    return ", ".join(result)

def overnight(items):
    result={}
    for day, reservations in items.items():
        try: next_day=(date.fromisoformat(day)+timedelta(days=1)).isoformat()
        except ValueError:continue
        for current in reservations:
            r=current.get('range') or {}
            if str(r.get('end',''))[:5]!='23:59':continue
            for following in items.get(next_day,[]):
                n=following.get('range') or {}
                if str(n.get('start',''))[:5]=='00:00':
                    result[day]=(str(r.get('start',''))[:5],str(n.get('end',''))[:5],next_day)
    return result

def run():
    cfg=settings(); today=date.today()
    data=get_calendar(today,today+timedelta(days=31)); child_id=selected_id(cfg,data)
    if child_id is None:print('Ei näkyviä lapsia tai varauksia seuranta-ajalla.');return
    current=collect(data,child_id); path=state_file('hoitoajat.json');previous=read_state(path)
    if previous is None:
        write_state(path,current);print('Ensimmäinen varaustilanne tallennettu, ei vanhoista tiedoista hälytyksiä.');return
    previous={k:v for k,v in previous.items() if k>=today.isoformat()}
    old_nights=overnight(previous);new_nights=overnight(current)
    for d, times in new_nights.items():
        if old_nights.get(d)!=times:
            start,end,until=times
            notify('eVaka-vahti: yöhoitovaraus',f'{d}–{until}\nAlkaa {start}, päättyy {end}')
    for day in sorted(set(previous)|set(current)):
        if previous.get(day,[])!=current.get(day,[]):
            notify('eVaka-vahti: hoitoajat muuttuneet',f'{day}\nEnnen: {as_text(previous.get(day,[]))}\nNyt: {as_text(current.get(day,[]))}')
    if previous==current:print('Ei muutoksia hoitoajoissa.')
    write_state(path,current)
