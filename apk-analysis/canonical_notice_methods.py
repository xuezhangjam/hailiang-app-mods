def methods(dex):
    result={}
    for c in dex.get_classes():
        for m in c.get_methods():
            seq=list(m.get_instructions_idx()) if m.get_code() else []
            indices={pc:j for j,(pc,i) in enumerate(seq)}
            payload_sources={}
            for pc,i in seq:
                if i.get_name() in ('packed-switch','sparse-switch'):
                    offset=next(x[1] for x in i.get_operands() if x[0]==3)
                    payload_sources[pc+offset*2]=pc
            canonical=[]
            for pc,i in seq:
                op=i.get_name().replace('const-string/jumbo','const-string')
                args=[]
                for x in i.get_operands():
                    if x[0]==3:args.append((3,indices.get(pc+x[1]*2,('raw',x[1]))))
                    elif len(x)>2:args.append((int(x[0]),x[2]))
                    else:args.append(tuple(x))
                if op in ('packed-switch-payload','sparse-switch-payload'):
                    args.append(('keys',i.get_keys()))
                    source=payload_sources[pc]
                    args.append(('targets',[indices[source+off*2] for off in i.get_targets()]))
                canonical.append((op,args))
            result[(c.get_name(),m.get_name(),m.get_descriptor())]=(m.get_access_flags(),m.get_code().get_registers_size() if m.get_code() else None,canonical)
    return result
