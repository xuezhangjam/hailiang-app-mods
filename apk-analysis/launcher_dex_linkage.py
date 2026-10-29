"""Resolve APK-owned members without treating constructors as inherited."""

PLATFORM_PREFIXES=('Landroid/','Ljava/','Ljavax/','Lorg/json/','[','Ldalvik/')
OBJECT_METHODS={
    ('<init>','()V'),('clone','()Ljava/lang/Object;'),
    ('equals','(Ljava/lang/Object;)Z'),('finalize','()V'),
    ('getClass','()Ljava/lang/Class;'),('hashCode','()I'),
    ('notify','()V'),('notifyAll','()V'),('toString','()Ljava/lang/String;'),
    ('wait','()V'),('wait','(J)V'),('wait','(JI)V'),
}

class DexLinker:
    def __init__(self,dex_files):
        self.defs={c.get_name():c for d in dex_files for c in d.get_classes()}
        self.members={name:{
            False:{(m.get_name(),m.get_descriptor().replace(' ','')) for m in c.get_methods()},
            True:{(f.get_name(),f.get_descriptor().replace(' ','')) for f in c.get_fields()},
        } for name,c in self.defs.items()}

    def resolve(self,cls,name,desc,field=False,seen=None):
        seen=set() if seen is None else seen
        if cls in seen:return False
        seen.add(cls)
        if cls not in self.defs:
            if cls=='Ljava/lang/Object;':
                return not field and (name,desc) in OBJECT_METHODS
            # Compiler-generated application bridges cannot come from Android
            # or Java, even when their owner implements a platform interface.
            if name.startswith(('lambda$','$r8$','access$')):return False
            return cls.startswith(PLATFORM_PREFIXES)
        if (name,desc) in self.members[cls][field]:return True
        # The superclass cannot supply an undeclared constructor for this type.
        if not field and name in ('<init>','<clinit>'):return False
        c=self.defs[cls]
        return self.resolve(c.get_superclassname(),name,desc,field,seen) or any(
            self.resolve(i,name,desc,field,seen) for i in c.get_interfaces())

    def check(self,classes):
        references=0
        errors=[]
        for c in classes:
            for method in c.get_methods():
                for instruction in method.get_instructions():
                    for op in instruction.get_operands():
                        if len(op)<3 or '->' not in str(op[2]):continue
                        cls,target=op[2].replace(' ','').split('->',1)
                        if '(' in target:
                            name,desc=target.split('(',1);desc='('+desc;field=False
                        else:
                            name,desc=op[2].split('->',1)[1].split(' ',1);field=True
                        if not self.resolve(cls,name,desc,field):
                            errors.append((c.get_name(),method.get_name(),op[2]))
                        references+=1
        return references,errors
