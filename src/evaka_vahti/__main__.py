# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT

"""Komentoriviliittymä."""
import sys
from . import hoitoajat,lasnaolo

def main():
    if len(sys.argv)!=2 or sys.argv[1] not in ('hoito','lasna'):
        print('Käyttö: python -m evaka_vahti hoito|lasna',file=sys.stderr)
        return 2
    try:
        (hoitoajat if sys.argv[1]=='hoito' else lasnaolo).run()
        return 0
    except Exception as error:
        # Älä tulosta palvelimen virhevastauksia, koska ne voivat sisältää yksityisiä tietoja.
        print('eVaka-vahti: tarkistus epäonnistui ('+type(error).__name__+').',file=sys.stderr)
        return 1
if __name__=='__main__':sys.exit(main())
