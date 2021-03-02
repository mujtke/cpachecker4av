typedef unsigned long pthread_t;

int x = 0;

void t1()
{
	int l1 = x + 1;
	x = l1 + x;
}

void t2()
{
	int l2 = x + 2;
	x = l2 + x;
}

int main()
{
	pthread_t tid1, tid2;
	
	pthread_create(&tid1, 0, t1, 0);
	pthread_create(&tid2, 0, t2, 0);
	
	pthread_join(tid1, 0);
	pthread_join(tid2, 0);
	
	return 0;
}

extern void __VERIFIER_error();

int x = 0;

int main()
{
	int i = 0;
	for(; i < 5; ++i) {
		if(x > 3) {
			ERROR: __VERIFIER_error();
		}
		x = x + 1;
	}
	return 0;
}

