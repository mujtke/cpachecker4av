typedef unsigned long pthread_t;


int x = 0;

void t1(void *arg)
{
	int l1 = x + 1;
	x = l1 + x;
}

void t2(void *arg)
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
