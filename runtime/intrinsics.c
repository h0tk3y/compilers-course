#include<stdio.h>
#include<stdlib.h>
#include<string.h>

void write(int val_type, int val) {
	printf("%d\n", val);
}

int read() {
  int val;
  printf("> ");
  scanf("%d", &val);
  return val;
}

extern size_t strlen(const char *str);

size_t strlen_2(int str_type, const char *str) {
    return strlen(str);
}

extern char * strdup(const char *str);

char *strdup_2(int str_type, const char *str) {
    return strdup(str);
}

extern int strcmp(const char *str1, const char *str2);

int strcmp_4(int str1_type, const char *str1, int str2_type, const char *str2) {
    return strcmp(str1, str2);
}

char strget(int s_type, const char *s, int i_type, size_t i) {
    return s[i];
}

int strset(int s_type, char *s, int i_type, size_t i, int c_type, char c) {
    s[i] = c;
    return 0;
}

char *strsub(int s_type, const char *s, int from_type, size_t from, int n_type, size_t n) {
    char* result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, s + from, n);
    return result;
}

char *strcat_4(int l_type, char *l, int r_type, const char *r) {
    size_t n1 = strlen(l);
    size_t n2 = strlen(r);
    size_t n = n1 + n2;
    char * result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, l, n1);
    memcpy(result + n1, r, n2);
    return result;
}

char *strmake(int n_type, size_t n, int c_type, char c) {
    char *result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memset(result, c, n);
    return result;
}

const int type_int = 0;
const int type_array = 1;

const int header_size = 3;

int *arrmake(int n_type, size_t n, int init_type, int init) {
    int* result = (int*) malloc((n + header_size) * sizeof(int));
    result[0] = type_int;
    result[1] = 0; // ref count
    result[2] = n; // size
    for (int i = 0; i <= n; i++) {
        result[i + header_size] = init;
    }
    // TODO: return the type_array as well
    return result;
}

int *Arrmake(int n_type, size_t n, int init_type, int init) {
    int* result = (int*) malloc((n + header_size) * sizeof(int) * 2); // * 2: boxed items store the type
    result[0] = type_array;
    result[1] = 0; // ref count
    result[2] = n; // size
    for (int i = 0; i <= n; i++) {
        int offset = header_size + (i * 2);
        result[offset + 0] = init;
        result[offset + 1] = init_type;
    }
    // TODO: perform ref counting
    // TODO: return the type_array as well
    return result;
}

int arrget(int array_type, int *array, int index_type, int index) {
    int arr_type = array[0];
    int item_index = header_size + (arr_type == type_int ? index : index * 2);
    int item = array[item_index];
    int type = (arr_type == type_int ? type_int : array[item_index + 1]);
    //TODO: return the type as well
    return item;
}

void ref_increase(int* array);
void ref_decrease(int* array);

int *arrset(int array_type, int *array, int index_type, int index, int value_type, int value) {
    int arr_type = array[0];
    if (arr_type == type_int && value_type == type_array) {
        // TODO: error
    }
    int item_index = header_size + (arr_type == type_int ? index : index * 2);
    if (arr_type == type_array && array[item_index + 1] == type_array) {
        ref_decrease((int*) array[item_index]);
    }
    array[item_index] = value;
    if (arr_type == type_array) {
        array[item_index + 1] = value_type;
        if (value_type == type_array) {
            ref_increase((int*) value);
        }
    }
    return 0;
}

int arrlen(int array_type, int *array) {
    return array[2];
}

void ref_decrease(int* array) {
    if (array[0] == -1) { // array is already in the procedure of ref operation
        return;
    }

    int arr_type = array[0];
    array[0] = -1;

    int ref_count = array[1];
    int size = array[2];

    for (int i = 0; i < size; ++i) {
        int item_index = header_size + (arr_type == type_int ? i : i * 2);
        int item_type = (arr_type == type_int ? type_int : array[item_index + 1]);
        if (item_type != type_int) {
            ref_decrease((int *) array[item_index]);
        }
    }

    int new_ref_count = ref_count - 1;
    if (new_ref_count == 0) {
        free(array);
    } else {
        array[0] = arr_type;
    }
}

void ref_increase(int* array) {
    if (array[0] == -1) { // array is already in the procedure of ref operation
        return;
    }

    int arr_type = array[0];
    array[0] = -1;

    int ref_count = array[1];
    int size = array[2];

    for (int i = 0; i < size; ++i) {
        int item_index = header_size + (arr_type == type_int ? i : i * 2);
        int item_type = (arr_type == type_int ? type_int : array[item_index + 1]);
        if (item_type != type_int) {
            ref_increase((int *) array[item_index]);
        }
    }

    int new_ref_count = ref_count + 1;
    array[0] = arr_type;
}